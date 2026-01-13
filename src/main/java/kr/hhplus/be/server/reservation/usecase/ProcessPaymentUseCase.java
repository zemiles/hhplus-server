package kr.hhplus.be.server.reservation.usecase;

import kr.hhplus.be.server.common.service.DistributedLockService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import kr.hhplus.be.server.point.domain.Ledger;
import kr.hhplus.be.server.point.domain.Wallet;
import kr.hhplus.be.server.reservation.domain.Payment;
import kr.hhplus.be.server.reservation.domain.PaymentStatus;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.port.LedgerRepositoryPort;
import kr.hhplus.be.server.reservation.port.PaymentRepositoryPort;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.port.WalletRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProcessPaymentUseCase {

	private final ReservationRepositoryPort reservationRepositoryPort;
	private final PaymentRepositoryPort paymentRepositoryPort;
	private final WalletRepositoryPort walletRepositoryPort;
	private final LedgerRepositoryPort ledgerRepositoryPort;
	private final DistributedLockService distributedLockService;
	private final PlatformTransactionManager transactionManager;
	
	// TransactionTemplate은 PlatformTransactionManager로부터 생성
	private TransactionTemplate getTransactionTemplate() {
		return new TransactionTemplate(transactionManager);
	}

	private static final String LOCK_KEY_PREFIX = "reservation:";

	/**
	 * 예약 결제 처리
	 * 
	 * 분산락을 사용하여 동시성 제어를 수행합니다.
	 * - 락 키: "reservation:{reservationId}"
	 * - 락 범위: 예약 조회부터 결제 완료까지의 전체 과정
	 * 
	 * 주의사항:
	 * - 분산락은 DB 트랜잭션 외부에서 획득되어야 합니다.
	 * - 락을 획득한 후 DB 트랜잭션 내에서 작업을 수행합니다.
	 * - 트랜잭션이 커밋된 후 락이 해제됩니다.
	 * - 같은 예약에 대해 동시에 결제가 발생하는 것을 방지합니다.
	 *
	 * @param reservationId 예약 ID
	 * @param idempotencyKey 멱등성 키
	 * @return 처리된 결제 정보
	 */
	public Payment execute(Long reservationId, String idempotencyKey) {
		// 분산락 키 생성: 예약 ID 기준
		String lockKey = LOCK_KEY_PREFIX + reservationId;
		
		// 분산락을 획득하고 작업 실행
		// 락은 트랜잭션 외부에서 획득되지만, 내부 작업은 트랜잭션 내에서 수행됩니다.
		return distributedLockService.executeWithLock(lockKey, () -> {
			// TransactionTemplate을 사용하여 명시적으로 트랜잭션 실행
			return getTransactionTemplate().execute(status -> {
				return executeInternal(reservationId, idempotencyKey);
			});
		});
	}

	/**
	 * 결제 처리 내부 로직 (트랜잭션 내부에서 실행)
	 * 
	 * 분산락 내부에서 실행되므로 새로운 트랜잭션을 시작해야 합니다.
	 *
	 * @param reservationId 예약 ID
	 * @param idempotencyKey 멱등성 키
	 * @return 처리된 결제 정보
	 */
	private Payment executeInternal(Long reservationId, String idempotencyKey) {
		// 1. 예약 조회
		Reservation reservation = reservationRepositoryPort.findById(reservationId)
				.orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다. reservationId : " + reservationId));

		// 2. 결제 가능 여부 확인 (비즈니스 로직)
		if (!reservation.canBePaid()) {
			if (reservation.isExpired()) {
				reservation.markAsExpired();
				reservationRepositoryPort.save(reservation);
				throw new IllegalArgumentException("예약이 만료되었습니다. reservationId : " + reservationId);
			}
			throw new IllegalStateException("결제할 수 없는 예약입니다. reservationId : " + reservationId);
		}

		// 3. 멱등성 체크(같은 결제 요청이 중복으로 들어오면 기존 결제 반환)
		if (idempotencyKey != null) {
			Optional<Payment> byIdempotencyKey = paymentRepositoryPort.findByIdempotencyKey(idempotencyKey);
			if(byIdempotencyKey.isPresent()) {
				return byIdempotencyKey.get();
			}
		}

		// 4. 지갑 조회
		Wallet wallet = walletRepositoryPort.findByUserId(reservation.getUserId())
				.orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다. userId : " + reservationId));

		// 5. 잔액 확인
		boolean deducted = walletRepositoryPort.deductBalanceIfSufficient(
				wallet.getId(),
				reservation.getAmountCents()
		);

		if(!deducted) {
			// 차감 실패 = 잔액 부족
			BigDecimal currentBalance = walletRepositoryPort.getBalance(wallet.getId());
			throw new IllegalStateException(String.format("잔액이 부족합니다. 현재 %s원, 필요 : %s원",
					currentBalance, reservation.getAmountCents()));
		}

		// 6. 잔액 차감
		walletRepositoryPort.deductBalance(wallet.getId(), reservation.getAmountCents());
		
		// 7. 결제 정보 생성
		Payment payment = new Payment();
		payment.setUserId(reservation.getUserId());
		payment.setReservationId(reservationId);
		payment.setTotalAmountCents(reservation.getAmountCents());
		payment.setStatus(PaymentStatus.INIT);
		payment.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());

		// 8.결제 저장
		payment = paymentRepositoryPort.save(payment);

		// 9. 결제 승인 처리
		payment.markAsApproved();
		payment = paymentRepositoryPort.save(payment);

		// 10. 거래 이력 기록
		Ledger ledger = new Ledger();
		ledger.setWallet(wallet);
		ledger.setAmount(reservation.getAmountCents());
		ledger.setType(kr.hhplus.be.server.point.domain.LedgerType.PAYMENT); // 결제 타입 설정
		ledger.setChargeDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
		ledger.setChargeTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));
		ledgerRepositoryPort.save(ledger);

		// 11. 예약 상태 업데이트
		reservation.markAsPaid();
		reservationRepositoryPort.save(reservation);

		return payment;
	}

}
