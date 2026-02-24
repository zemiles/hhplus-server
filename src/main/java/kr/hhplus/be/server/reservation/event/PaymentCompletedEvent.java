package kr.hhplus.be.server.reservation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * 결제 완료 이벤트
 * 
 * 결제가 완료되었을 때 발행되는 이벤트입니다.
 * 트랜잭션 커밋 후 비동기로 처리되어야 하는 작업들을 위해 사용됩니다.
 * - 랭킹 업데이트
 * - 데이터 플랫폼 전송
 */
@Getter
public class PaymentCompletedEvent extends ApplicationEvent {

	private final Long paymentId;
	private final Long userId;
	private final Long reservationId;
	private final Long concertScheduleId;
	private final BigDecimal totalAmountCents;
	private final String idempotencyKey;

	public PaymentCompletedEvent(Object source, Long paymentId, Long userId, Long reservationId, 
	                             Long concertScheduleId, BigDecimal totalAmountCents, String idempotencyKey) {
		super(source);
		this.paymentId = paymentId;
		this.userId = userId;
		this.reservationId = reservationId;
		this.concertScheduleId = concertScheduleId;
		this.totalAmountCents = totalAmountCents;
		this.idempotencyKey = idempotencyKey;
	}
}
