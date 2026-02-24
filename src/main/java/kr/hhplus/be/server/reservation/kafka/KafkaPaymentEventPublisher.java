package kr.hhplus.be.server.reservation.kafka;

import kr.hhplus.be.server.reservation.event.PaymentCompletedEvent;
import kr.hhplus.be.server.reservation.event.PaymentCompletedMessage;
import kr.hhplus.be.server.reservation.port.PaymentEventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Kafka를 통한 결제 완료 이벤트 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.event.provider", havingValue = "kafka", matchIfMissing = true)
public class KafkaPaymentEventPublisher implements PaymentEventPublisherPort {

	private final KafkaTemplate<String, PaymentCompletedMessage> kafkaTemplate;

	@Value("${spring.kafka.topic.payment-completed:payment-completed}")
	private String topicName;

	@Override
	public void publish(PaymentCompletedEvent event) {
		try {
			PaymentCompletedMessage message = PaymentCompletedMessage.from(event);
			String key = event.getReservationId().toString();
			kafkaTemplate.send(topicName, key, message)
					.whenComplete((result, ex) -> {
						if (ex != null) {
							log.error("Kafka 발행 실패: paymentId={}, reservationId={}",
									event.getPaymentId(), event.getReservationId(), ex);
						} else {
							log.debug("Kafka 발행 성공: paymentId={}, reservationId={}",
									event.getPaymentId(), event.getReservationId());
						}
					});
		} catch (Exception e) {
			log.error("Kafka 발행 처리 중 오류: paymentId={}", event.getPaymentId(), e);
		}
	}
}
