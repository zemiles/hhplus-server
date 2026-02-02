package kr.hhplus.be.server.reservation.usecase;

import kr.hhplus.be.server.common.service.DistributedLockService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import kr.hhplus.be.server.point.domain.Ledger;
import kr.hhplus.be.server.point.domain.Wallet;
import kr.hhplus.be.server.reservation.domain.Payment;
import kr.hhplus.be.server.reservation.domain.PaymentStatus;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.port.LedgerRepositoryPort;
import kr.hhplus.be.server.reservation.port.PaymentRepositoryPort;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.port.SeatRepositoryPort;
import kr.hhplus.be.server.reservation.port.WalletRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessPaymentUseCase {

	private final ReservationRepositoryPort reservationRepositoryPort;
	private final PaymentRepositoryPort paymentRepositoryPort;
	private final WalletRepositoryPort walletRepositoryPort;
	private final LedgerRepositoryPort ledgerRepositoryPort;
	private final SeatRepositoryPort seatRepositoryPort;
	private final ConcertRankingService concertRankingService;
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
		// 1. 멱등성 체크 (가장 먼저 수행 - 잔액 차감 전에 중복 요청을 방지)
		// 멱등성 키가 null이면 UUID를 생성하지만, 이는 멱등성을 보장하지 않으므로
		// 실제 운영 환경에서는 idempotencyKey를 필수로 받아야 합니다.
		String finalIdempotencyKey = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
		Optional<Payment> existingPayment = paymentRepositoryPort.findByIdempotencyKey(finalIdempotencyKey);
		if(existingPayment.isPresent()) {
			// 이미 존재하는 결제 반환 (멱등성 보장)
			return existingPayment.get();
		}

		// 2. 예약 조회
		Reservation reservation = reservationRepositoryPort.findById(reservationId)
				.orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다. reservationId : " + reservationId));

		// 3. 결제 가능 여부 확인 (비즈니스 로직)
		if (!reservation.canBePaid()) {
			if (reservation.isExpired()) {
				// 예약 만료 처리
				// 주의: 예외를 던지면 트랜잭션이 롤백되므로 상태 업데이트가 의미가 없습니다.
				// 만료 상태를 저장하려면 별도의 트랜잭션에서 처리하거나 예외를 던지지 않아야 합니다.
				// 현재는 예외를 던지므로 상태 업데이트는 제거합니다.
				throw new IllegalArgumentException("예약이 만료되었습니다. reservationId : " + reservationId);
			}
			throw new IllegalStateException("결제할 수 없는 예약입니다. reservationId : " + reservationId);
		}

		// 4. 지갑 조회
		Wallet wallet = walletRepositoryPort.findByUserId(reservation.getUserId())
				.orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다. userId : " + reservation.getUserId()));

		// 5. 잔액 확인 및 차감 (원자적 연산)
		// deductBalanceIfSufficient는 잔액이 충분할 때만 차감하고 true를 반환합니다.
		// 잔액이 부족하면 차감하지 않고 false를 반환합니다.
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

		// 주의: deductBalanceIfSufficient가 이미 잔액을 차감했으므로
		// 추가로 deductBalance를 호출하면 안 됩니다. (중복 차감 방지)
		
		// 6. 결제 정보 생성 및 승인 (처음부터 APPROVED 상태로 생성)
		// INIT 상태로 저장한 후 즉시 APPROVED로 변경하는 것은 불필요한 두 번의 저장입니다.
		Payment payment = new Payment();
		payment.setUserId(reservation.getUserId());
		payment.setReservationId(reservationId);
		payment.setTotalAmountCents(reservation.getAmountCents());
		payment.setIdempotencyKey(finalIdempotencyKey);
		// 처음부터 APPROVED 상태로 설정
		payment.markAsApproved();

		// 7. 결제 저장
		payment = paymentRepositoryPort.save(payment);

		// 8. 거래 이력 기록
		Ledger ledger = new Ledger();
		ledger.setWallet(wallet); // 트랜잭션 내에서 처리되므로 지연 로딩 문제 없음
		ledger.setAmount(reservation.getAmountCents());
		ledger.setType(kr.hhplus.be.server.point.domain.LedgerType.PAYMENT); // 결제 타입 설정
		ledger.setChargeDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
		ledger.setChargeTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));
		ledgerRepositoryPort.save(ledger);

		// 9. 예약 상태 업데이트
		reservation.markAsPaid();
		reservationRepositoryPort.save(reservation);

		// 10. 매진 여부 확인 및 랭킹 업데이트 (비동기 처리 권장)
		// 트랜잭션 외부에서 처리하여 랭킹 업데이트 실패가 결제 실패로 이어지지 않도록 함
		try {
			checkAndUpdateRanking(reservation.getConcertSchedule().getConcertScheduleId());
		} catch (Exception e) {
			log.error("랭킹 업데이트 실패: concertScheduleId={}", reservation.getConcertSchedule().getConcertScheduleId(), e);
			// 랭킹 업데이트 실패는 치명적이지 않으므로 예외를 다시 던지지 않음
		}

		return payment;
	}

	/**
	 * 콘서트 일정의 매진 여부를 확인하고, 매진이면 랭킹에 추가
	 * 
	 * @param concertScheduleId 콘서트 일정 ID
	 */
	private void checkAndUpdateRanking(Long concertScheduleId) {
		// 전체 좌석 개수 조회
		long totalSeats = seatRepositoryPort.countByConcertScheduleId(concertScheduleId);
		
		if (totalSeats == 0) {
			// 좌석이 없으면 매진 확인 불가
			return;
		}

		// 결제 완료된 예약 개수 조회
		long paidReservations = reservationRepositoryPort.countByConcertScheduleIdAndStatus(
				concertScheduleId, 
				ReservationStatus.PAID
		);

		// 모든 좌석이 결제 완료되었는지 확인
		if (paidReservations >= totalSeats) {
			// 매진! 랭킹에 추가
			concertRankingService.addSoldOutConcert(concertScheduleId);
			log.info("콘서트 매진: concertScheduleId={}, totalSeats={}, paidReservations={}", 
					concertScheduleId, totalSeats, paidReservations);
		}
	}

}
