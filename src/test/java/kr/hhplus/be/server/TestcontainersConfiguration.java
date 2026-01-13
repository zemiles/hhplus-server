package kr.hhplus.be.server;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis Testcontainers 설정
 * 
 * 로컬 환경에서는 로컬 Redis를 사용하도록 비활성화합니다.
 * Testcontainers는 CI/CD 환경에서만 사용하도록 설정할 수 있습니다.
 * 
 * 로컬에서 테스트할 때는 docker-compose로 실행한 로컬 Redis를 사용합니다.
 */
@Configuration
public class TestcontainersConfiguration {

	public static GenericContainer<?> REDIS_CONTAINER;

	/**
	 * 로컬 환경에서는 Testcontainers를 사용하지 않고 로컬 Redis를 사용합니다.
	 * application.yml의 h2 프로파일 설정에서 localhost:6379를 사용하도록 되어 있습니다.
	 */
	@PostConstruct
	public void init() {
		// 로컬 환경에서는 Testcontainers를 사용하지 않음
		// 로컬 Redis (localhost:6379)를 직접 사용
		// CI/CD 환경에서만 Testcontainers를 사용하려면 환경 변수로 제어 가능
		String useTestcontainers = System.getProperty("use.testcontainers", "false");
		
		if ("true".equals(useTestcontainers)) {
			// Redis 컨테이너가 아직 시작되지 않았다면 시작
			if (REDIS_CONTAINER == null || !REDIS_CONTAINER.isRunning()) {
				REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
					.withExposedPorts(6379);
				REDIS_CONTAINER.start();

				// 시스템 프로퍼티 설정
				System.setProperty("spring.data.redis.host", REDIS_CONTAINER.getHost());
				System.setProperty("spring.data.redis.port", String.valueOf(REDIS_CONTAINER.getMappedPort(6379)));
			}
		}
		// 로컬 환경에서는 아무것도 하지 않음 (application.yml의 설정 사용)
	}

	@PreDestroy
	public void preDestroy() {
		if (REDIS_CONTAINER != null && REDIS_CONTAINER.isRunning()) {
			REDIS_CONTAINER.stop();
		}
	}
}