package kr.hhplus.be.server.ranking.controller;

import kr.hhplus.be.server.ranking.dto.RankingResponse;
import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RankingController 단위 테스트
 * 
 * 다양한 테스트 케이스를 포함:
 * - 정상적인 랭킹 조회
 * - 빈 랭킹 조회
 * - limit 파라미터 테스트
 * - 랭킹 순서 검증
 * - 예외 상황 처리
 */
@ExtendWith(MockitoExtension.class)
class RankingControllerTest {

	@Mock
	private ConcertRankingService concertRankingService;

	@InjectMocks
	private RankingController rankingController;

	@BeforeEach
	void setUp() {
		// 기본 설정
	}

	@Test
	@DisplayName("랭킹 조회 시 기본 limit 10으로 조회됨")
	void testGetTopSoldOutRanking_DefaultLimit_ReturnsTop10() {
		// given
		List<ConcertRankingService.RankingEntry> entries = createRankingEntries(10);
		when(concertRankingService.getTopSoldOutRankingWithScore(10)).thenReturn(entries);
		when(concertRankingService.getRank(anyLong())).thenReturn(1L);

		// when
		List<RankingResponse> result = rankingController.getTopSoldOutRanking(10);

		// then
		assertThat(result).hasSize(10);
		verify(concertRankingService).getTopSoldOutRankingWithScore(10);
	}

	@Test
	@DisplayName("랭킹이 비어있을 때 빈 리스트 반환")
	void testGetTopSoldOutRanking_EmptyRanking_ReturnsEmptyList() {
		// given
		when(concertRankingService.getTopSoldOutRankingWithScore(anyInt())).thenReturn(List.of());

		// when
		List<RankingResponse> result = rankingController.getTopSoldOutRanking(10);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("limit이 1일 때 상위 1개만 반환")
	void testGetTopSoldOutRanking_LimitOne_ReturnsOne() {
		// given
		List<ConcertRankingService.RankingEntry> entries = createRankingEntries(1);
		when(concertRankingService.getTopSoldOutRankingWithScore(1)).thenReturn(entries);
		when(concertRankingService.getRank(anyLong())).thenReturn(1L);

		// when
		List<RankingResponse> result = rankingController.getTopSoldOutRanking(1);

		// then
		assertThat(result).hasSize(1);
		verify(concertRankingService).getTopSoldOutRankingWithScore(1);
	}

	@Test
	@DisplayName("limit이 100일 때 상위 100개 반환")
	void testGetTopSoldOutRanking_Limit100_Returns100() {
		// given
		List<ConcertRankingService.RankingEntry> entries = createRankingEntries(100);
		when(concertRankingService.getTopSoldOutRankingWithScore(100)).thenReturn(entries);
		when(concertRankingService.getRank(anyLong())).thenReturn(1L);

		// when
		List<RankingResponse> result = rankingController.getTopSoldOutRanking(100);

		// then
		assertThat(result).hasSize(100);
		verify(concertRankingService).getTopSoldOutRankingWithScore(100);
	}

	@Test
	@DisplayName("랭킹 응답에 올바른 정보가 포함됨")
	void testGetTopSoldOutRanking_ResponseContainsCorrectInfo() {
		// given
		ConcertRankingService.RankingEntry entry = new ConcertRankingService.RankingEntry(1L, 1000L);
		when(concertRankingService.getTopSoldOutRankingWithScore(10)).thenReturn(List.of(entry));
		when(concertRankingService.getRank(1L)).thenReturn(1L);

		// when
		List<RankingResponse> result = rankingController.getTopSoldOutRanking(10);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getConcertScheduleId()).isEqualTo(1L);
		assertThat(result.get(0).getRank()).isEqualTo(1L);
		assertThat(result.get(0).getSoldOutTimestamp()).isEqualTo(1000L);
		assertThat(result.get(0).getSoldOutDateTime()).isNotNull();
	}

	@Test
	@DisplayName("여러 랭킹이 있을 때 각각의 랭킹이 올바르게 반환됨")
	void testGetTopSoldOutRanking_MultipleRankings_ReturnsCorrectRanks() {
		// given
		List<ConcertRankingService.RankingEntry> entries = new ArrayList<>();
		entries.add(new ConcertRankingService.RankingEntry(1L, 1000L));
		entries.add(new ConcertRankingService.RankingEntry(2L, 2000L));
		entries.add(new ConcertRankingService.RankingEntry(3L, 3000L));
		
		when(concertRankingService.getTopSoldOutRankingWithScore(10)).thenReturn(entries);
		when(concertRankingService.getRank(1L)).thenReturn(1L);
		when(concertRankingService.getRank(2L)).thenReturn(2L);
		when(concertRankingService.getRank(3L)).thenReturn(3L);

		// when
		List<RankingResponse> result = rankingController.getTopSoldOutRanking(10);

		// then
		assertThat(result).hasSize(3);
		assertThat(result.get(0).getRank()).isEqualTo(1L);
		assertThat(result.get(1).getRank()).isEqualTo(2L);
		assertThat(result.get(2).getRank()).isEqualTo(3L);
	}

	@Test
	@DisplayName("랭킹 조회 시 서비스 예외가 발생해도 예외가 전파됨")
	void testGetTopSoldOutRanking_ServiceException_PropagatesException() {
		// given
		when(concertRankingService.getTopSoldOutRankingWithScore(anyInt()))
				.thenThrow(new RuntimeException("Service error"));

		// when & then
		org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
			rankingController.getTopSoldOutRanking(10);
		});
	}

	@Test
	@DisplayName("매진 시간이 0이어도 정상적으로 처리됨")
	void testGetTopSoldOutRanking_ZeroTimestamp_HandlesCorrectly() {
		// given
		ConcertRankingService.RankingEntry entry = new ConcertRankingService.RankingEntry(1L, 0L);
		when(concertRankingService.getTopSoldOutRankingWithScore(10)).thenReturn(List.of(entry));
		when(concertRankingService.getRank(1L)).thenReturn(1L);

		// when
		List<RankingResponse> result = rankingController.getTopSoldOutRanking(10);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getSoldOutTimestamp()).isEqualTo(0L);
	}

	@Test
	@DisplayName("매진 시간이 매우 큰 값이어도 정상적으로 처리됨")
	void testGetTopSoldOutRanking_VeryLargeTimestamp_HandlesCorrectly() {
		// given
		long largeTimestamp = Long.MAX_VALUE;
		ConcertRankingService.RankingEntry entry = new ConcertRankingService.RankingEntry(1L, largeTimestamp);
		when(concertRankingService.getTopSoldOutRankingWithScore(10)).thenReturn(List.of(entry));
		when(concertRankingService.getRank(1L)).thenReturn(1L);

		// when
		List<RankingResponse> result = rankingController.getTopSoldOutRanking(10);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getSoldOutTimestamp()).isEqualTo(largeTimestamp);
	}

	@Test
	@DisplayName("랭킹이 -1을 반환해도 정상적으로 처리됨")
	void testGetTopSoldOutRanking_RankMinusOne_HandlesCorrectly() {
		// given
		ConcertRankingService.RankingEntry entry = new ConcertRankingService.RankingEntry(1L, 1000L);
		when(concertRankingService.getTopSoldOutRankingWithScore(10)).thenReturn(List.of(entry));
		when(concertRankingService.getRank(1L)).thenReturn(-1L); // 랭킹에 없음

		// when
		List<RankingResponse> result = rankingController.getTopSoldOutRanking(10);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getRank()).isEqualTo(-1L);
	}

	// Helper method
	private List<ConcertRankingService.RankingEntry> createRankingEntries(int count) {
		List<ConcertRankingService.RankingEntry> entries = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			entries.add(new ConcertRankingService.RankingEntry((long) i, (long) (i * 1000)));
		}
		return entries;
	}
}
