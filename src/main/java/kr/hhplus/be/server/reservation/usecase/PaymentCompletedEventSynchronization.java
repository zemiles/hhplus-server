package kr.hhplus.be.server.reservation.usecase;

import kr.hhplus.be.server.reservation.event.PaymentCompletedEvent;
import kr.hhplus.be.server.reservation.port.PaymentEventPublisherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;

import java.math.BigDecimal;

/**
 * 결제 완료 이벤트 발행용 트랜잭션 동기화
 *
 * 트랜잭션 커밋 후에만 이벤트를 발행합니다.
 * - source: paymentId (디버깅 시 식별 용이)
 * - TransactionSynchronization 인터페이스 명시적 구현
 */
@Slf4j
public class PaymentCompletedEventSynchronization implements TransactionSynchronization {

	private final PaymentEventPublisherPort paymentEventPublisher;
	private final Long paymentId;
	private final Long userId;
	private final Long reservationId;
	private final Long concertScheduleId;
	private final BigDecimal totalAmountCents;
	private final String idempotencyKey;
	private final Long reservationIdForLog;

	public PaymentCompletedEventSynchronization(
			PaymentEventPublisherPort paymentEventPublisher,
			Long paymentId,
			Long userId,
			Long reservationId,
			Long concertScheduleId,
			BigDecimal totalAmountCents,
			String idempotencyKey,
			Long reservationIdForLog) {
		this.paymentEventPublisher = paymentEventPublisher;
		this.paymentId = paymentId;
		this.userId = userId;
		this.reservationId = reservationId;
		this.concertScheduleId = concertScheduleId;
		this.totalAmountCents = totalAmountCents;
		this.idempotencyKey = idempotencyKey;
		this.reservationIdForLog = reservationIdForLog;
	}

	@Override
	public void afterCommit() {
		// source: paymentId - 디버깅 시 이벤트 추적에 유리
		PaymentCompletedEvent event = new PaymentCompletedEvent(
				paymentId,
				paymentId,
				userId,
				reservationId,
				concertScheduleId,
				totalAmountCents,
				idempotencyKey
		);
		paymentEventPublisher.publish(event);
		log.debug("결제 완료 이벤트 발행: paymentId={}, reservationId={}",
				paymentId, reservationIdForLog);
	}
}
