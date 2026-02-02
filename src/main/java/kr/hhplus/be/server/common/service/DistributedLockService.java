package kr.hhplus.be.server.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis 기반 분산락 서비스
 * 
 * 분산 환경에서 동시성 제어를 위한 분산락을 제공합니다.
 * Redis의 SETNX 명령을 사용하여 락을 획득하고, TTL을 설정하여 데드락을 방지합니다.
 * 
 * 주의사항:
 * - DB 트랜잭션과 함께 사용할 때는 락 해제 시점을 주의해야 합니다.
 * - 락을 획득한 후 DB 트랜잭션이 커밋되기 전에 락이 해제되면 동시성 문제가 발생할 수 있습니다.
 * - 따라서 락은 DB 트랜잭션이 완료된 후에 해제되어야 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

	private final RedisTemplate<String, Object> redisTemplate;

	/**
	 * 락 키의 기본 TTL (초)
	 * 락을 획득한 후 일정 시간이 지나면 자동으로 해제되어 데드락을 방지합니다.
	 */
	private static final long DEFAULT_LOCK_TTL_SECONDS = 30;

	/**
	 * 락 획득 시도 간격 (밀리초)
	 * 락 획득에 실패했을 때 재시도하기 전 대기 시간입니다.
	 */
	private static final long LOCK_RETRY_INTERVAL_MS = 100;

	/**
	 * 락 획득 최대 대기 시간 (밀리초)
	 * 이 시간 동안 락 획득을 시도하고, 실패하면 예외를 발생시킵니다.
	 */
	private static final long MAX_WAIT_TIME_MS = 5000;

	/**
	 * 분산락을 획득하고 작업을 실행한 후 자동으로 락을 해제합니다.
	 * 
	 * @param lockKey 락 키 (예: "seat:123", "reservation:456")
	 * @param supplier 락을 획득한 후 실행할 작업
	 * @return 작업 실행 결과
	 * @throws RuntimeException 락 획득 실패 시
	 */
	public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
		return executeWithLock(lockKey, DEFAULT_LOCK_TTL_SECONDS, supplier);
	}

	/**
	 * 분산락을 획득하고 작업을 실행한 후 자동으로 락을 해제합니다.
	 * 
	 * @param lockKey 락 키
	 * @param ttlSeconds 락 TTL (초)
	 * @param supplier 락을 획득한 후 실행할 작업
	 * @return 작업 실행 결과
	 * @throws RuntimeException 락 획득 실패 시
	 */
	public <T> T executeWithLock(String lockKey, long ttlSeconds, Supplier<T> supplier) {
		boolean lockAcquired = false;
		try {
			// 락 획득 시도
			lockAcquired = tryLock(lockKey, ttlSeconds);
			
			if (!lockAcquired) {
				throw new IllegalStateException("락 획득에 실패했습니다. lockKey: " + lockKey);
			}

			log.debug("락 획득 성공: {}", lockKey);
			
			// 작업 실행
			return supplier.get();
			
		} finally {
			// 락 해제
			if (lockAcquired) {
				unlock(lockKey);
				log.debug("락 해제 완료: {}", lockKey);
			}
		}
	}

	/**
	 * 분산락을 획득하고 작업을 실행한 후 자동으로 락을 해제합니다. (반환값 없음)
	 * 
	 * @param lockKey 락 키
	 * @param runnable 락을 획득한 후 실행할 작업
	 * @throws RuntimeException 락 획득 실패 시
	 */
	public void executeWithLock(String lockKey, Runnable runnable) {
		executeWithLock(lockKey, () -> {
			runnable.run();
			return null;
		});
	}

	/**
	 * 분산락을 획득하고 작업을 실행한 후 자동으로 락을 해제합니다. (반환값 없음, TTL 지정)
	 * 
	 * @param lockKey 락 키
	 * @param ttlSeconds 락 TTL (초)
	 * @param runnable 락을 획득한 후 실행할 작업
	 * @throws RuntimeException 락 획득 실패 시
	 */
	public void executeWithLock(String lockKey, long ttlSeconds, Runnable runnable) {
		executeWithLock(lockKey, ttlSeconds, () -> {
			runnable.run();
			return null;
		});
	}

	/**
	 * 락 획득 시도
	 * 
	 * @param lockKey 락 키
	 * @param ttlSeconds TTL (초)
	 * @return 락 획득 성공 여부
	 */
	private boolean tryLock(String lockKey, long ttlSeconds) {
		long startTime = System.currentTimeMillis();
		
		while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME_MS) {
			// SETNX: 키가 존재하지 않을 때만 설정 (원자적 연산)
			Boolean success = redisTemplate.opsForValue().setIfAbsent(
					lockKey, 
					"locked", 
					Duration.ofSeconds(ttlSeconds)
			);
			
			if (Boolean.TRUE.equals(success)) {
				return true;
			}
			
			// 락 획득 실패 시 잠시 대기 후 재시도
			try {
				Thread.sleep(LOCK_RETRY_INTERVAL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		
		return false;
	}

	/**
	 * 락 해제
	 * 
	 * @param lockKey 락 키
	 */
	private void unlock(String lockKey) {
		try {
			redisTemplate.delete(lockKey);
		} catch (Exception e) {
			log.error("락 해제 중 오류 발생: lockKey={}", lockKey, e);
			// 락 해제 실패는 치명적이지 않으므로 예외를 다시 던지지 않음
			// TTL이 지나면 자동으로 해제되기 때문
		}
	}
}
