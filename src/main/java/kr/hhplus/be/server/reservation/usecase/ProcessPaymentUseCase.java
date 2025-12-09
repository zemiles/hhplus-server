package kr.hhplus.be.server.reservation.usecase;

import jakarta.transaction.Transactional;
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

	/**
	 * 예약 결제 처리
	 *
	 * @Param reservationId 예약 ID
	 * @Param idempotencyKey 멱등성 키
	 * @return 처리된 결제 정보
	 */

	@Transactional
	public Payment execute(Long reservationId, String idempotencyKey) {
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
		BigDecimal currentBalance = walletRepositoryPort.getBalance(wallet.getId());
		if(currentBalance.compareTo(reservation.getAmountCents()) < 0 ) {
			throw new IllegalStateException(String.format("잔액이 부족합니다. 현재 %b원, 필요 : %b원", currentBalance, reservation.getAmountCents()));
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

		ledger.setChargeDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
		ledger.setChargeTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));
		ledgerRepositoryPort.save(ledger);

		// 11. 예약 상태 업데이트
		reservation.markAsPaid();
		reservationRepositoryPort.save(reservation);

		return payment;


	}


}
