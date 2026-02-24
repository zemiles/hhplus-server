package kr.hhplus.be.server.reservation.port;

import kr.hhplus.be.server.reservation.event.PaymentCompletedEvent;

/**
 * 결제 완료 이벤트 발행 Port
 *
 * 결제 완료 시 이벤트를 외부로 전달합니다.
 * 구현체: Kafka, Spring Event 등
 */
public interface PaymentEventPublisherPort {

	/**
	 * 결제 완료 이벤트를 발행합니다.
	 *
	 * @param event 결제 완료 이벤트
	 */
	void publish(PaymentCompletedEvent event);
}
