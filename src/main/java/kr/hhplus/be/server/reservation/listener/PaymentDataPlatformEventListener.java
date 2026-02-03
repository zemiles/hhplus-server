package kr.hhplus.be.server.reservation.listener;

import kr.hhplus.be.server.reservation.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 결제 완료 이벤트 리스너 - 데이터 플랫폼 전송
 * 
 * 결제 완료 후 예약 정보를 데이터 플랫폼에 전송합니다.
 * Mock API를 호출하여 실제 데이터 플랫폼 전송을 시뮬레이션합니다.
 * 
 * 트랜잭션과 관심사를 분리하기 위해 이벤트로 처리됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentDataPlatformEventListener {

	@SuppressWarnings("unused")
	private final RestTemplate restTemplate;
	
	// Mock API 엔드포인트 (실제 운영 환경에서는 설정 파일에서 관리)
	@SuppressWarnings("unused")
	private static final String DATA_PLATFORM_API_URL = "https://api.example.com/data-platform/reservations";

	/**
	 * 결제 완료 이벤트를 수신하여 데이터 플랫폼에 전송합니다.
	 * 
	 * @param event 결제 완료 이벤트
	 */
	@Async("taskExecutor")
	@EventListener
	public void handlePaymentCompleted(PaymentCompletedEvent event) {
		try {
			// 데이터 플랫폼에 전송할 데이터 구성
			Map<String, Object> payload = new HashMap<>();
			payload.put("paymentId", event.getPaymentId());
			payload.put("userId", event.getUserId());
			payload.put("reservationId", event.getReservationId());
			payload.put("concertScheduleId", event.getConcertScheduleId());
			payload.put("totalAmountCents", event.getTotalAmountCents());
			payload.put("idempotencyKey", event.getIdempotencyKey());
			payload.put("timestamp", System.currentTimeMillis());

			// Mock API 호출 (실제 운영 환경에서는 실제 데이터 플랫폼 API 호출)
			// 현재는 Mock이므로 실제 호출하지 않고 로그만 남깁니다.
			log.info("데이터 플랫폼 전송 (Mock): paymentId={}, reservationId={}, concertScheduleId={}", 
					event.getPaymentId(), event.getReservationId(), event.getConcertScheduleId());
			log.debug("전송 데이터: {}", payload);

			// 실제 API 호출이 필요한 경우 아래 주석을 해제하세요
			// try {
			//     restTemplate.postForObject(DATA_PLATFORM_API_URL, payload, String.class);
			//     log.info("데이터 플랫폼 전송 성공: paymentId={}", event.getPaymentId());
			// } catch (Exception e) {
			//     log.error("데이터 플랫폼 전송 실패: paymentId={}", event.getPaymentId(), e);
			//     // 재시도 로직이나 Dead Letter Queue 처리 등을 고려할 수 있습니다.
			// }

		} catch (Exception e) {
			log.error("데이터 플랫폼 전송 처리 중 오류 발생: paymentId={}", 
					event.getPaymentId(), e);
			// 데이터 플랫폼 전송 실패는 치명적이지 않으므로 예외를 다시 던지지 않음
		}
	}
}
