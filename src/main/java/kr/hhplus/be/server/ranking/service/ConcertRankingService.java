package kr.hhplus.be.server.ranking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 콘서트 빠른 매진 랭킹 서비스
 * 
 * Redis Sorted Set을 사용하여 빠른 매진 랭킹을 관리합니다.
 * - Key: "ranking:soldout:concert_schedule"
 * - Score: 매진 시간 (timestamp, 밀리초)
 * - Member: concertScheduleId (String)
 * 
 * 랭킹은 매진 시간이 빠른 순서대로 정렬됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertRankingService {

	private final RedisTemplate<String, Object> redisTemplate;
	
	private static final String RANKING_KEY = "ranking:soldout:concert_schedule";

	/**
	 * 콘서트 일정이 매진되었을 때 랭킹에 추가
	 * 
	 * @param concertScheduleId 콘서트 일정 ID
	 */
	public void addSoldOutConcert(Long concertScheduleId) {
		try {
			long soldOutTimestamp = System.currentTimeMillis();
			
			// Redis Sorted Set에 추가 (score는 매진 시간)
			redisTemplate.opsForZSet().add(RANKING_KEY, concertScheduleId.toString(), soldOutTimestamp);
			
			log.info("매진 랭킹 추가: concertScheduleId={}, soldOutTimestamp={}", concertScheduleId, soldOutTimestamp);
		} catch (Exception e) {
			log.error("매진 랭킹 추가 실패: concertScheduleId={}", concertScheduleId, e);
			// 랭킹 추가 실패는 치명적이지 않으므로 예외를 다시 던지지 않음
		}
	}

	/**
	 * 빠른 매진 랭킹 조회 (상위 N개)
	 * 
	 * @param limit 조회할 개수 (기본값: 10)
	 * @return 콘서트 일정 ID 리스트 (매진 시간이 빠른 순서)
	 */
	public List<Long> getTopSoldOutRanking(int limit) {
		try {
			// ZREVRANGE: score가 큰 순서대로 조회 (최신 매진 순서)
			// 하지만 우리는 빠른 매진 순서를 원하므로, score가 작은 순서대로 조회해야 함
			// ZRANGE: score가 작은 순서대로 조회 (빠른 매진 순서)
			Set<Object> members = redisTemplate.opsForZSet().range(RANKING_KEY, 0, limit - 1);
			
			if (members == null || members.isEmpty()) {
				return List.of();
			}
			
			return members.stream()
					.map(member -> Long.parseLong(member.toString()))
					.collect(Collectors.toList());
		} catch (Exception e) {
			log.error("랭킹 조회 실패", e);
			return List.of();
		}
	}

	/**
	 * 빠른 매진 랭킹 조회 (상위 N개, 점수 포함)
	 * 
	 * @param limit 조회할 개수
	 * @return 콘서트 일정 ID와 매진 시간의 쌍 리스트
	 */
	public List<RankingEntry> getTopSoldOutRankingWithScore(int limit) {
		try {
			// ZRANGE: score가 작은 순서대로 조회 (빠른 매진 순서)
			Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
					.rangeWithScores(RANKING_KEY, 0, limit - 1);
			
			if (tuples == null || tuples.isEmpty()) {
				return List.of();
			}
			
			return tuples.stream()
					.map(tuple -> new RankingEntry(
							Long.parseLong(tuple.getValue().toString()),
							tuple.getScore().longValue()
					))
					.collect(Collectors.toList());
		} catch (Exception e) {
			log.error("랭킹 조회 실패", e);
			return List.of();
		}
	}

	/**
	 * 특정 콘서트 일정의 랭킹 조회
	 * 
	 * @param concertScheduleId 콘서트 일정 ID
	 * @return 랭킹 (1부터 시작, 없으면 -1)
	 */
	public long getRank(Long concertScheduleId) {
		try {
			Long rank = redisTemplate.opsForZSet().rank(RANKING_KEY, concertScheduleId.toString());
			return rank != null ? rank + 1 : -1; // Redis rank는 0부터 시작하므로 +1
		} catch (Exception e) {
			log.error("랭킹 조회 실패: concertScheduleId={}", concertScheduleId, e);
			return -1;
		}
	}

	/**
	 * 랭킹 초기화 (테스트용)
	 */
	public void clearRanking() {
		redisTemplate.delete(RANKING_KEY);
		log.info("랭킹 초기화 완료");
	}

	/**
	 * 랭킹 엔트리 (콘서트 일정 ID와 매진 시간)
	 */
	public static class RankingEntry {
		private final Long concertScheduleId;
		private final Long soldOutTimestamp;

		public RankingEntry(Long concertScheduleId, Long soldOutTimestamp) {
			this.concertScheduleId = concertScheduleId;
			this.soldOutTimestamp = soldOutTimestamp;
		}

		public Long getConcertScheduleId() {
			return concertScheduleId;
		}

		public Long getSoldOutTimestamp() {
			return soldOutTimestamp;
		}
	}
}
