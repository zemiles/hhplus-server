package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.reservation.port.EventIdempotencyPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 기반 이벤트 멱등성 어댑터
 *
 * Redis SET NX EX를 사용하여 동일 idempotencyKey의 중복 처리를 방지합니다.
 * TTL 24시간으로 설정하여 오래된 키는 자동 만료됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.event.idempotency.enabled", havingValue = "true", matchIfMissing = true)
public class RedisEventIdempotencyAdapter implements EventIdempotencyPort {

	private static final String KEY_PREFIX = "event:processed:";
	private static final long TTL_HOURS = 24;

	private final RedisTemplate<String, Object> redisTemplate;

	@Override
	public boolean tryAcquireProcessing(String idempotencyKey, String processorType) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return true; // 키가 없으면 멱등성 체크 스킵 (처리 진행)
		}

		String key = KEY_PREFIX + processorType + ":" + idempotencyKey;
		Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(TTL_HOURS));

		if (Boolean.TRUE.equals(success)) {
			log.debug("이벤트 처리 권한 획득: processorType={}, idempotencyKey={}", processorType, idempotencyKey);
			return true;
		}

		log.debug("이벤트 중복 처리 스킵 (멱등성): processorType={}, idempotencyKey={}", processorType, idempotencyKey);
		return false;
	}
}
