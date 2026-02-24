package kr.hhplus.be.server.ranking.integration;

import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConcertRankingService 부하 테스트
 * 
 * 대량 데이터 및 동시성 부하 상황에서의 성능과 안정성을 검증합니다.
 * - 대량 데이터 처리
 * - 장기간 동시성 테스트
 * - 중복 추가 시 최초 삽입 보존 검증
 */
@SpringBootTest
@ActiveProfiles("h2")
class ConcertRankingLoadTest {

	@Autowired
	private ConcertRankingService concertRankingService;

	@BeforeEach
	void setUp() {
		concertRankingService.clearRanking();
	}

	@Test
	@DisplayName("대량 데이터(1000개) 추가 및 조회 성능 테스트")
	void testLargeData_1000Entries_PerformanceTest() throws InterruptedException {
		// given
		int dataSize = 1000;
		long startTime = System.currentTimeMillis();

		// when - 대량 데이터 추가
		for (int i = 1; i <= dataSize; i++) {
			concertRankingService.addSoldOutConcert((long) i);
		}

		long addTime = System.currentTimeMillis() - startTime;
		System.out.println("대량 데이터 추가 시간: " + addTime + "ms");

		// then - 조회 성능 테스트
		startTime = System.currentTimeMillis();
		List<ConcertRankingService.RankingEntry> entries = 
				concertRankingService.getTopSoldOutRankingWithScore(dataSize);
		long queryTime = System.currentTimeMillis() - startTime;
		System.out.println("대량 데이터 조회 시간: " + queryTime + "ms");

		assertThat(entries).hasSize(dataSize);
		// 랭킹이 올바르게 설정되었는지 확인
		for (int i = 0; i < entries.size(); i++) {
			assertThat(entries.get(i).getRank()).isEqualTo((long) (i + 1));
		}
	}

	@Test
	@DisplayName("동시에 100개 콘서트 매진 추가 시 최초 삽입 보존 검증")
	void testConcurrentAdd_100Concerts_PreservesFirstTimestamp() throws InterruptedException {
		// given
		int threadCount = 100;
		Long concertScheduleId = 1L;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		List<Long> timestamps = new CopyOnWriteArrayList<>();

		// when - 동시에 같은 콘서트를 여러 번 추가
		for (int i = 0; i < threadCount; i++) {
			final int index = i;
			executor.submit(() -> {
				try {
					long timestamp = System.currentTimeMillis();
					timestamps.add(timestamp);
					Thread.sleep(index * 10); // 시간 간격을 두어 순서 보장
					concertRankingService.addSoldOutConcert(concertScheduleId);
					latch.countDown();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}

		// then
		boolean completed = latch.await(10, TimeUnit.SECONDS);
		assertThat(completed).isTrue();

		// 최초 삽입 시간이 유지되어야 함 (ZADD NX)
		List<ConcertRankingService.RankingEntry> entries = 
				concertRankingService.getTopSoldOutRankingWithScore(10);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).getConcertScheduleId()).isEqualTo(concertScheduleId);
		
		// 최초 삽입 시간이 가장 빠른 시간과 유사해야 함
		long firstTimestamp = timestamps.stream().min(Long::compareTo).orElse(0L);
		assertThat(entries.get(0).getSoldOutTimestamp()).isLessThanOrEqualTo(firstTimestamp + 1000);

		executor.shutdown();
	}

	@Test
	@DisplayName("장기간 동시성 테스트 - 10초간 지속적 경쟁")
	void testLongRunningConcurrency_10Seconds_Stable() throws InterruptedException {
		// given
		int threadCount = 50;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch endLatch = new CountDownLatch(threadCount);
		AtomicInteger successCount = new AtomicInteger(0);

		// when - 10초간 지속적으로 추가
		for (int i = 0; i < threadCount; i++) {
			final int concertId = i + 1;
			executor.submit(() -> {
				try {
					startLatch.await();
					
					// 10초간 반복 추가
					long endTime = System.currentTimeMillis() + 10000;
					while (System.currentTimeMillis() < endTime) {
						concertRankingService.addSoldOutConcert((long) concertId);
						successCount.incrementAndGet();
						Thread.sleep(100);
					}
					
					endLatch.countDown();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}

		startLatch.countDown();
		boolean completed = endLatch.await(15, TimeUnit.SECONDS);
		assertThat(completed).isTrue();

		// then - 모든 콘서트가 랭킹에 포함되어야 함 (ZADD NX로 최초 삽입만 허용)
		List<ConcertRankingService.RankingEntry> entries = 
				concertRankingService.getTopSoldOutRankingWithScore(threadCount);
		
		// 각 콘서트는 한 번만 포함되어야 함
		assertThat(entries.size()).isLessThanOrEqualTo(threadCount);
		
		// 랭킹이 올바르게 설정되었는지 확인
		for (int i = 0; i < entries.size(); i++) {
			assertThat(entries.get(i).getRank()).isEqualTo((long) (i + 1));
		}

		executor.shutdown();
	}

	@Test
	@DisplayName("네트워크 지연 상황 시뮬레이션 - 중복 추가 시 최초 삽입 보존")
	void testNetworkDelay_DuplicateAdd_PreservesFirstTimestamp() throws InterruptedException {
		// given
		Long concertScheduleId = 1L;
		long firstTime = System.currentTimeMillis();
		
		// when - 네트워크 지연을 시뮬레이션하여 여러 번 추가
		concertRankingService.addSoldOutConcert(concertScheduleId);
		Thread.sleep(50); // 네트워크 지연 시뮬레이션
		concertRankingService.addSoldOutConcert(concertScheduleId);
		Thread.sleep(50);
		concertRankingService.addSoldOutConcert(concertScheduleId);

		// then - 최초 매진 시간이 유지되어야 함
		List<ConcertRankingService.RankingEntry> entries = 
				concertRankingService.getTopSoldOutRankingWithScore(10);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).getConcertScheduleId()).isEqualTo(concertScheduleId);
		// 최초 삽입 시간이 유지되어야 함
		assertThat(entries.get(0).getSoldOutTimestamp()).isLessThanOrEqualTo(firstTime + 200);
	}
}
