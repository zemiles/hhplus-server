package kr.hhplus.be.server.reservation.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaymentCompletedEvent 테스트
 * 
 * 이벤트 클래스의 기본 동작을 검증합니다.
 */
class PaymentCompletedEventTest {

	@Test
	@DisplayName("이벤트 생성 시 모든 필드가 올바르게 설정됨")
	void testEventCreation_SetsAllFieldsCorrectly() {
		// given
		Object source = new Object();
		Long paymentId = 1L;
		Long userId = 100L;
		Long reservationId = 200L;
		Long concertScheduleId = 300L;
		BigDecimal totalAmountCents = new BigDecimal(80000);
		String idempotencyKey = "test-key-123";

		// when
		PaymentCompletedEvent event = new PaymentCompletedEvent(
				source,
				paymentId,
				userId,
				reservationId,
				concertScheduleId,
				totalAmountCents,
				idempotencyKey
		);

		// then
		assertThat(event.getSource()).isEqualTo(source);
		assertThat(event.getPaymentId()).isEqualTo(paymentId);
		assertThat(event.getUserId()).isEqualTo(userId);
		assertThat(event.getReservationId()).isEqualTo(reservationId);
		assertThat(event.getConcertScheduleId()).isEqualTo(concertScheduleId);
		assertThat(event.getTotalAmountCents()).isEqualTo(totalAmountCents);
		assertThat(event.getIdempotencyKey()).isEqualTo(idempotencyKey);
	}
}
