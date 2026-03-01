package kr.hhplus.be.server.reservation.kafka;

import kr.hhplus.be.server.reservation.event.PaymentCompletedMessage;
import kr.hhplus.be.server.reservation.port.EventIdempotencyPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 결제 완료 Kafka Consumer - 데이터 플랫폼 전송
 *
 * payment-completed 토픽을 구독하여 예약/결제 정보를 데이터 플랫폼으로 전달합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.event.provider", havingValue = "kafka", matchIfMissing = true)
public class PaymentDataPlatformKafkaConsumer {

	private static final String PROCESSOR_TYPE = "data-platform";

	private final EventIdempotencyPort eventIdempotencyPort;

	@KafkaListener(
			topics = "${spring.kafka.topic.payment-completed:payment-completed}",
			groupId = "payment-data-platform-consumer-group"
	)
	public void consume(PaymentCompletedMessage message) {
		try {
			// 멱등성: Kafka 재시도 시 중복 전송 방지
			if (!eventIdempotencyPort.tryAcquireProcessing(message.getIdempotencyKey(), PROCESSOR_TYPE)) {
				log.debug("데이터 플랫폼 전송 스킵 (이미 처리됨): paymentId={}, idempotencyKey={}",
						message.getPaymentId(), message.getIdempotencyKey());
				return;
			}

			Map<String, Object> payload = new HashMap<>();
			payload.put("paymentId", message.getPaymentId());
			payload.put("userId", message.getUserId());
			payload.put("reservationId", message.getReservationId());
			payload.put("concertScheduleId", message.getConcertScheduleId());
			payload.put("totalAmountCents", message.getTotalAmountCents());
			payload.put("idempotencyKey", message.getIdempotencyKey());
			payload.put("timestamp", message.getTimestamp());

			log.info("데이터 플랫폼 전송 (Kafka): paymentId={}, reservationId={}, concertScheduleId={}",
					message.getPaymentId(), message.getReservationId(), message.getConcertScheduleId());
			log.debug("전송 데이터: {}", payload);
		} catch (Exception e) {
			log.error("데이터 플랫폼 전송 처리 중 오류: paymentId={}", message.getPaymentId(), e);
		}
	}
}
