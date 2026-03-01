package kr.hhplus.be.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * 스케줄러 및 비동기 처리 설정
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulerConfig {

	@Value("${app.async.core-pool-size:5}")
	private int corePoolSize;
	@Value("${app.async.max-pool-size:10}")
	private int maxPoolSize;
	@Value("${app.async.queue-capacity:100}")
	private int queueCapacity;

	/**
	 * 비동기 처리를 위한 ThreadPoolTaskExecutor 설정
	 * 이벤트 리스너의 비동기 처리를 위해 사용됩니다.
	 * prod 프로파일: core=10, max=20, queue=500
	 */
	@Bean(name = "taskExecutor")
	public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(corePoolSize);
		executor.setMaxPoolSize(maxPoolSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix("async-event-");
		executor.initialize();
		return executor;
	}

	/**
	 * 데이터 플랫폼 API 호출을 위한 RestTemplate
	 */
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
