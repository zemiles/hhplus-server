package kr.hhplus.be.server.reservation.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Kafka에 발행되는 결제 완료 메시지
 *
 * PaymentCompletedEvent의 데이터를 Kafka 토픽으로 전달하기 위한 DTO입니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedMessage {

	private Long paymentId;
	private Long userId;
	private Long reservationId;
	private Long concertScheduleId;
	private BigDecimal totalAmountCents;
	private String idempotencyKey;
	private Long timestamp;

	public static PaymentCompletedMessage from(PaymentCompletedEvent event) {
		return PaymentCompletedMessage.builder()
				.paymentId(event.getPaymentId())
				.userId(event.getUserId())
				.reservationId(event.getReservationId())
				.concertScheduleId(event.getConcertScheduleId())
				.totalAmountCents(event.getTotalAmountCents())
				.idempotencyKey(event.getIdempotencyKey())
				.timestamp(System.currentTimeMillis())
				.build();
	}
}
