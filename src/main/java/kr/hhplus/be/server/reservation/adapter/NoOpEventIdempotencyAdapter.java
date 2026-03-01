package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.reservation.port.EventIdempotencyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 멱등성 체크 비활성화 시 사용하는 No-Op 어댑터
 *
 * Redis 미사용 환경(예: 일부 테스트)에서 사용합니다.
 * 항상 처리 권한을 부여합니다 (tryAcquireProcessing → true).
 */
@Component
@ConditionalOnProperty(name = "app.event.idempotency.enabled", havingValue = "false")
public class NoOpEventIdempotencyAdapter implements EventIdempotencyPort {

	@Override
	public boolean tryAcquireProcessing(String idempotencyKey, String processorType) {
		return true;
	}
}
