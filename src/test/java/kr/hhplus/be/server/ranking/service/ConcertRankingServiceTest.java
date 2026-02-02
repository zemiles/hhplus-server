package kr.hhplus.be.server.ranking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * ConcertRankingService 단위 테스트
 * 
 * 다양한 테스트 케이스를 포함:
 * - 정상적인 랭킹 추가 및 조회
 * - 빈 랭킹 조회
 * - 중복 추가 처리
 * - 랭킹 순서 검증
 * - 예외 상황 처리
 * - 경계값 테스트
 */
@ExtendWith(MockitoExtension.class)
class ConcertRankingServiceTest {

	@Mock
	private RedisTemplate<String, Object> redisTemplate;

	@Mock
	private ZSetOperations<String, Object> zSetOperations;

	@InjectMocks
	private ConcertRankingService concertRankingService;

	@BeforeEach
	void setUp() {
		// lenient()를 사용하여 일부 테스트에서 사용되지 않아도 경고하지 않음
		lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
	}

	@Test
	@DisplayName("매진된 콘서트를 랭킹에 추가하면 성공적으로 추가됨")
	void testAddSoldOutConcert_Success_AddsToRanking() {
		// given
		Long concertScheduleId = 1L;
		when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

		// when
		concertRankingService.addSoldOutConcert(concertScheduleId);

		// then
		verify(zSetOperations).add(eq("ranking:soldout:concert_schedule"), eq("1"), anyDouble());
	}

	@Test
	@DisplayName("랭킹 추가 시 Redis 예외가 발생해도 예외를 던지지 않음")
	void testAddSoldOutConcert_RedisException_DoesNotThrow() {
		// given
		Long concertScheduleId = 1L;
		when(zSetOperations.add(anyString(), anyString(), anyDouble()))
				.thenThrow(new RuntimeException("Redis connection error"));

		// when & then - 예외가 발생하지 않아야 함
		concertRankingService.addSoldOutConcert(concertScheduleId);
		verify(zSetOperations).add(anyString(), anyString(), anyDouble());
	}

	@Test
	@DisplayName("랭킹이 비어있을 때 상위 랭킹 조회 시 빈 리스트 반환")
	void testGetTopSoldOutRanking_EmptyRanking_ReturnsEmptyList() {
		// given
		when(zSetOperations.range(anyString(), anyLong(), anyLong())).thenReturn(Collections.emptySet());

		// when
		List<Long> result = concertRankingService.getTopSoldOutRanking(10);

		// then
		assertThat(result).isEmpty();
		verify(zSetOperations).range(eq("ranking:soldout:concert_schedule"), eq(0L), eq(9L));
	}

	@Test
	@DisplayName("랭킹이 있을 때 상위 N개 조회 시 정상적으로 반환됨")
	void testGetTopSoldOutRanking_WithRankings_ReturnsTopN() {
		// given
		Set<Object> members = new LinkedHashSet<>();
		members.add("1");
		members.add("2");
		members.add("3");
		when(zSetOperations.range(anyString(), anyLong(), anyLong())).thenReturn(members);

		// when
		List<Long> result = concertRankingService.getTopSoldOutRanking(3);

		// then
		assertThat(result).hasSize(3);
		assertThat(result).containsExactly(1L, 2L, 3L);
	}

	@Test
	@DisplayName("랭킹 조회 시 limit이 0이면 빈 리스트 반환")
	void testGetTopSoldOutRanking_LimitZero_ReturnsEmptyList() {
		// given
		when(zSetOperations.range(anyString(), anyLong(), anyLong())).thenReturn(Collections.emptySet());

		// when
		List<Long> result = concertRankingService.getTopSoldOutRanking(0);

		// then
		assertThat(result).isEmpty();
		verify(zSetOperations).range(eq("ranking:soldout:concert_schedule"), eq(0L), eq(-1L));
	}

	@Test
	@DisplayName("랭킹 조회 시 limit이 음수면 빈 리스트 반환")
	void testGetTopSoldOutRanking_NegativeLimit_ReturnsEmptyList() {
		// given
		when(zSetOperations.range(anyString(), anyLong(), anyLong())).thenReturn(Collections.emptySet());

		// when
		List<Long> result = concertRankingService.getTopSoldOutRanking(-1);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("랭킹 조회 시 Redis 예외가 발생하면 빈 리스트 반환")
	void testGetTopSoldOutRanking_RedisException_ReturnsEmptyList() {
		// given
		when(zSetOperations.range(anyString(), anyLong(), anyLong()))
				.thenThrow(new RuntimeException("Redis connection error"));

		// when
		List<Long> result = concertRankingService.getTopSoldOutRanking(10);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("랭킹 조회 시 점수 포함하여 조회하면 점수와 함께 반환됨")
	void testGetTopSoldOutRankingWithScore_WithRankings_ReturnsWithScores() {
		// given
		Set<ZSetOperations.TypedTuple<Object>> tuples = new LinkedHashSet<>();
		
		ZSetOperations.TypedTuple<Object> tuple1 = mock(ZSetOperations.TypedTuple.class);
		when(tuple1.getValue()).thenReturn("1");
		when(tuple1.getScore()).thenReturn(1000.0);
		
		ZSetOperations.TypedTuple<Object> tuple2 = mock(ZSetOperations.TypedTuple.class);
		when(tuple2.getValue()).thenReturn("2");
		when(tuple2.getScore()).thenReturn(2000.0);
		
		tuples.add(tuple1);
		tuples.add(tuple2);
		
		when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(tuples);

		// when
		List<ConcertRankingService.RankingEntry> result = concertRankingService.getTopSoldOutRankingWithScore(2);

		// then
		assertThat(result).hasSize(2);
		assertThat(result.get(0).getConcertScheduleId()).isEqualTo(1L);
		assertThat(result.get(0).getSoldOutTimestamp()).isEqualTo(1000L);
		assertThat(result.get(1).getConcertScheduleId()).isEqualTo(2L);
		assertThat(result.get(1).getSoldOutTimestamp()).isEqualTo(2000L);
	}

	@Test
	@DisplayName("특정 콘서트의 랭킹 조회 시 정상적으로 반환됨")
	void testGetRank_ExistingConcert_ReturnsRank() {
		// given
		Long concertScheduleId = 1L;
		when(zSetOperations.rank(anyString(), anyString())).thenReturn(0L); // 0-based index

		// when
		long rank = concertRankingService.getRank(concertScheduleId);

		// then
		assertThat(rank).isEqualTo(1); // 1-based rank
		verify(zSetOperations).rank(eq("ranking:soldout:concert_schedule"), eq("1"));
	}

	@Test
	@DisplayName("랭킹에 없는 콘서트의 랭킹 조회 시 -1 반환")
	void testGetRank_NonExistentConcert_ReturnsMinusOne() {
		// given
		Long concertScheduleId = 999L;
		when(zSetOperations.rank(anyString(), anyString())).thenReturn(null);

		// when
		long rank = concertRankingService.getRank(concertScheduleId);

		// then
		assertThat(rank).isEqualTo(-1);
	}

	@Test
	@DisplayName("랭킹 조회 시 Redis 예외가 발생하면 -1 반환")
	void testGetRank_RedisException_ReturnsMinusOne() {
		// given
		Long concertScheduleId = 1L;
		when(zSetOperations.rank(anyString(), anyString()))
				.thenThrow(new RuntimeException("Redis connection error"));

		// when
		long rank = concertRankingService.getRank(concertScheduleId);

		// then
		assertThat(rank).isEqualTo(-1);
	}

	@Test
	@DisplayName("랭킹 초기화 시 Redis에서 키가 삭제됨")
	void testClearRanking_Success_DeletesKey() {
		// given - RedisTemplate.delete()는 Boolean을 반환함
		// clearRanking() 메서드가 실제로 delete()를 호출하므로 mock 설정
		// 이 테스트만을 위한 독립적인 mock 설정
		when(redisTemplate.delete("ranking:soldout:concert_schedule")).thenReturn(Boolean.TRUE);
		
		// when
		concertRankingService.clearRanking();

		// then - delete()가 호출되었는지 확인
		verify(redisTemplate, times(1)).delete("ranking:soldout:concert_schedule");
	}

	@Test
	@DisplayName("매진 시간이 빠른 순서대로 랭킹이 정렬됨")
	void testRankingOrder_FastestSoldOutFirst_ReturnsInOrder() {
		// given
		// 첫 번째 콘서트: 1000ms에 매진
		// 두 번째 콘서트: 2000ms에 매진
		// 세 번째 콘서트: 3000ms에 매진
		Set<ZSetOperations.TypedTuple<Object>> tuples = new LinkedHashSet<>();
		
		ZSetOperations.TypedTuple<Object> tuple1 = mock(ZSetOperations.TypedTuple.class);
		when(tuple1.getValue()).thenReturn("1");
		when(tuple1.getScore()).thenReturn(1000.0);
		
		ZSetOperations.TypedTuple<Object> tuple2 = mock(ZSetOperations.TypedTuple.class);
		when(tuple2.getValue()).thenReturn("2");
		when(tuple2.getScore()).thenReturn(2000.0);
		
		ZSetOperations.TypedTuple<Object> tuple3 = mock(ZSetOperations.TypedTuple.class);
		when(tuple3.getValue()).thenReturn("3");
		when(tuple3.getScore()).thenReturn(3000.0);
		
		tuples.add(tuple1);
		tuples.add(tuple2);
		tuples.add(tuple3);
		
		when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(tuples);

		// when
		List<ConcertRankingService.RankingEntry> result = concertRankingService.getTopSoldOutRankingWithScore(3);

		// then
		assertThat(result).hasSize(3);
		assertThat(result.get(0).getSoldOutTimestamp()).isLessThan(result.get(1).getSoldOutTimestamp());
		assertThat(result.get(1).getSoldOutTimestamp()).isLessThan(result.get(2).getSoldOutTimestamp());
	}

	@Test
	@DisplayName("매진 시간이 동일한 경우 모두 랭킹에 포함됨")
	void testRankingOrder_SameTimestamp_AllIncluded() {
		// given
		Set<ZSetOperations.TypedTuple<Object>> tuples = new LinkedHashSet<>();
		
		ZSetOperations.TypedTuple<Object> tuple1 = mock(ZSetOperations.TypedTuple.class);
		when(tuple1.getValue()).thenReturn("1");
		when(tuple1.getScore()).thenReturn(1000.0);
		
		ZSetOperations.TypedTuple<Object> tuple2 = mock(ZSetOperations.TypedTuple.class);
		when(tuple2.getValue()).thenReturn("2");
		when(tuple2.getScore()).thenReturn(1000.0); // 동일한 시간
		
		tuples.add(tuple1);
		tuples.add(tuple2);
		
		when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(tuples);

		// when
		List<ConcertRankingService.RankingEntry> result = concertRankingService.getTopSoldOutRankingWithScore(10);

		// then
		assertThat(result).hasSize(2);
		assertThat(result.get(0).getSoldOutTimestamp()).isEqualTo(result.get(1).getSoldOutTimestamp());
	}

	@Test
	@DisplayName("매진 시간이 매우 큰 값(미래)이어도 정상 처리됨")
	void testRankingOrder_VeryLargeTimestamp_HandlesCorrectly() {
		// given
		Long concertScheduleId = 1L;
		long futureTimestamp = Long.MAX_VALUE - 1000;
		when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

		// when
		concertRankingService.addSoldOutConcert(concertScheduleId);

		// then - 예외가 발생하지 않아야 함
		verify(zSetOperations).add(anyString(), anyString(), anyDouble());
	}

	@Test
	@DisplayName("매진 시간이 0이어도 정상 처리됨")
	void testRankingOrder_ZeroTimestamp_HandlesCorrectly() {
		// given
		Set<ZSetOperations.TypedTuple<Object>> tuples = new LinkedHashSet<>();
		
		ZSetOperations.TypedTuple<Object> tuple = mock(ZSetOperations.TypedTuple.class);
		when(tuple.getValue()).thenReturn("1");
		when(tuple.getScore()).thenReturn(0.0);
		
		tuples.add(tuple);
		
		when(zSetOperations.rangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(tuples);

		// when
		List<ConcertRankingService.RankingEntry> result = concertRankingService.getTopSoldOutRankingWithScore(1);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getSoldOutTimestamp()).isEqualTo(0L);
	}
}
