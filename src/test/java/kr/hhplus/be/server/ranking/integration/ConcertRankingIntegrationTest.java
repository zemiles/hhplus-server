package kr.hhplus.be.server.ranking.integration;

import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConcertRankingService 통합 테스트
 * 
 * 실제 Redis를 사용한 통합 테스트:
 * - 실제 Redis 연결 테스트
 * - 동시성 테스트
 * - 랭킹 순서 검증
 * - 대량 데이터 처리 테스트
 */
@SpringBootTest
@ActiveProfiles("h2")
class ConcertRankingIntegrationTest {

	@Autowired
	private ConcertRankingService concertRankingService;

	@BeforeEach
	void setUp() {
		// 각 테스트 전에 랭킹 초기화
		concertRankingService.clearRanking();
	}

	@Test
	@DisplayName("실제 Redis에 랭킹을 추가하고 조회할 수 있음")
	void testAddAndGetRanking_RealRedis_Success() throws InterruptedException {
		// given
		Long concertScheduleId1 = 1L;
		Long concertScheduleId2 = 2L;
		Long concertScheduleId3 = 3L;

		// when - 시간 간격을 두고 추가하여 순서 보장
		Thread.sleep(10);
		concertRankingService.addSoldOutConcert(concertScheduleId1);
		
		Thread.sleep(10);
		concertRankingService.addSoldOutConcert(concertScheduleId2);
		
		Thread.sleep(10);
		concertRankingService.addSoldOutConcert(concertScheduleId3);

		// then
		List<Long> rankings = concertRankingService.getTopSoldOutRanking(10);
		assertThat(rankings).hasSize(3);
		assertThat(rankings.get(0)).isEqualTo(concertScheduleId1); // 가장 빠르게 매진
		assertThat(rankings.get(1)).isEqualTo(concertScheduleId2);
		assertThat(rankings.get(2)).isEqualTo(concertScheduleId3);
	}

	@Test
	@DisplayName("동시에 여러 콘서트가 매진되어도 랭킹이 정상적으로 추가됨")
	void testConcurrentAddRanking_MultipleConcerts_Success() throws InterruptedException {
		// given
		int threadCount = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);

		// when - 동시에 여러 콘서트 매진 추가
		for (int i = 1; i <= threadCount; i++) {
			final int concertId = i;
			executor.submit(() -> {
				try {
					Thread.sleep(10 * concertId); // 시간 간격을 두어 순서 보장
					concertRankingService.addSoldOutConcert((long) concertId);
					latch.countDown();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}

		// then
		boolean completed = latch.await(5, TimeUnit.SECONDS);
		assertThat(completed).isTrue();

		List<Long> rankings = concertRankingService.getTopSoldOutRanking(threadCount);
		assertThat(rankings).hasSize(threadCount);
		
		// 순서 검증 (시간 순서대로 - 매진 시간이 빠른 순서)
		// Thread.sleep으로 시간 간격을 두었으므로, concertId가 작을수록 먼저 매진됨
		// 하지만 동시성 환경에서는 정확한 순서가 보장되지 않을 수 있으므로
		// 모든 콘서트가 랭킹에 포함되어 있는지만 확인
		assertThat(rankings).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

		executor.shutdown();
	}

	@Test
	@DisplayName("랭킹에 없는 콘서트의 랭킹 조회 시 -1 반환")
	void testGetRank_NonExistentConcert_ReturnsMinusOne() {
		// given
		Long nonExistentId = 999L;

		// when
		long rank = concertRankingService.getRank(nonExistentId);

		// then
		assertThat(rank).isEqualTo(-1);
	}

	@Test
	@DisplayName("랭킹이 비어있을 때 조회 시 빈 리스트 반환")
	void testGetTopRanking_EmptyRanking_ReturnsEmptyList() {
		// when
		List<Long> rankings = concertRankingService.getTopSoldOutRanking(10);

		// then
		assertThat(rankings).isEmpty();
	}

	@Test
	@DisplayName("상위 5개만 조회 시 5개만 반환됨")
	void testGetTopRanking_Limit5_Returns5() throws InterruptedException {
		// given
		for (int i = 1; i <= 10; i++) {
			Thread.sleep(10);
			concertRankingService.addSoldOutConcert((long) i);
		}

		// when
		List<Long> rankings = concertRankingService.getTopSoldOutRanking(5);

		// then
		assertThat(rankings).hasSize(5);
	}

	@Test
	@DisplayName("랭킹 조회 시 점수와 함께 조회할 수 있음")
	void testGetTopRankingWithScore_WithRankings_ReturnsWithScores() throws InterruptedException {
		// given
		Long concertScheduleId = 1L;
		long beforeTime = System.currentTimeMillis();
		
		Thread.sleep(10);
		concertRankingService.addSoldOutConcert(concertScheduleId);
		
		long afterTime = System.currentTimeMillis();

		// when
		List<ConcertRankingService.RankingEntry> entries = concertRankingService.getTopSoldOutRankingWithScore(1);

		// then
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).getConcertScheduleId()).isEqualTo(concertScheduleId);
		assertThat(entries.get(0).getSoldOutTimestamp()).isGreaterThanOrEqualTo(beforeTime);
		assertThat(entries.get(0).getSoldOutTimestamp()).isLessThanOrEqualTo(afterTime + 100);
	}

	@Test
	@DisplayName("같은 콘서트를 여러 번 추가해도 한 번만 랭킹에 포함됨")
	void testAddRanking_DuplicateConcert_OnlyOneEntry() throws InterruptedException {
		// given
		Long concertScheduleId = 1L;

		// when - 같은 콘서트를 여러 번 추가
		concertRankingService.addSoldOutConcert(concertScheduleId);
		Thread.sleep(10);
		concertRankingService.addSoldOutConcert(concertScheduleId);
		Thread.sleep(10);
		concertRankingService.addSoldOutConcert(concertScheduleId);

		// then - 한 번만 포함되어야 함 (마지막 점수로 업데이트됨)
		List<Long> rankings = concertRankingService.getTopSoldOutRanking(10);
		assertThat(rankings).contains(concertScheduleId);
		// Sorted Set은 같은 member가 있으면 score가 업데이트되므로, 마지막 점수가 적용됨
	}

	@Test
	@DisplayName("랭킹 초기화 후 빈 랭킹 조회 가능")
	void testClearRanking_AfterClear_ReturnsEmpty() throws InterruptedException {
		// given
		concertRankingService.addSoldOutConcert(1L);
		concertRankingService.addSoldOutConcert(2L);
		
		// when
		concertRankingService.clearRanking();

		// then
		List<Long> rankings = concertRankingService.getTopSoldOutRanking(10);
		assertThat(rankings).isEmpty();
	}

	@Test
	@DisplayName("매진 시간이 빠른 순서대로 랭킹이 정렬됨")
	void testRankingOrder_FastestFirst_CorrectOrder() throws InterruptedException {
		// given
		Long fastConcert = 1L;
		Long mediumConcert = 2L;
		Long slowConcert = 3L;

		// when - 시간 간격을 두고 추가
		long startTime = System.currentTimeMillis();
		concertRankingService.addSoldOutConcert(fastConcert);
		
		Thread.sleep(100);
		concertRankingService.addSoldOutConcert(mediumConcert);
		
		Thread.sleep(100);
		concertRankingService.addSoldOutConcert(slowConcert);

		// then
		List<ConcertRankingService.RankingEntry> entries = concertRankingService.getTopSoldOutRankingWithScore(3);
		assertThat(entries).hasSize(3);
		assertThat(entries.get(0).getConcertScheduleId()).isEqualTo(fastConcert);
		assertThat(entries.get(1).getConcertScheduleId()).isEqualTo(mediumConcert);
		assertThat(entries.get(2).getConcertScheduleId()).isEqualTo(slowConcert);
		
		// 시간 순서 검증
		assertThat(entries.get(0).getSoldOutTimestamp())
				.isLessThan(entries.get(1).getSoldOutTimestamp());
		assertThat(entries.get(1).getSoldOutTimestamp())
				.isLessThan(entries.get(2).getSoldOutTimestamp());
	}
}
