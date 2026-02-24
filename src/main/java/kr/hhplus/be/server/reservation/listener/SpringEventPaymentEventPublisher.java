package kr.hhplus.be.server.reservation.listener;

import kr.hhplus.be.server.reservation.event.PaymentCompletedEvent;
import kr.hhplus.be.server.reservation.port.PaymentEventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring Application Event를 통한 결제 완료 이벤트 발행
 *
 * app.event.provider=spring-event 일 때 사용됩니다.
 * Kafka 미사용 시 또는 테스트 환경에서 활용할 수 있습니다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.event.provider", havingValue = "spring-event")
public class SpringEventPaymentEventPublisher implements PaymentEventPublisherPort {

	private final ApplicationEventPublisher applicationEventPublisher;

	@Override
	public void publish(PaymentCompletedEvent event) {
		applicationEventPublisher.publishEvent(event);
	}
}
