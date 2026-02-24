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
	 * "가장 빠른 매진(최소 timestamp)"을 보존하기 위해 ZADD NX 옵션을 사용합니다.
	 * NX 옵션은 member가 존재하지 않을 때만 추가하므로, 최초 매진 시간이 유지됩니다.
	 * 
	 * @param concertScheduleId 콘서트 일정 ID
	 */
	public void addSoldOutConcert(Long concertScheduleId) {
		try {
			long soldOutTimestamp = System.currentTimeMillis();
			
			// Redis Sorted Set에 추가 (score는 매진 시간)
			// ZADD NX: member가 존재하지 않을 때만 추가 (최초 삽입만 허용)
			// 이를 통해 가장 빠른 매진 시간을 보존합니다.
			Boolean added = redisTemplate.opsForZSet().addIfAbsent(
					RANKING_KEY, 
					concertScheduleId.toString(), 
					soldOutTimestamp
			);
			
			if (Boolean.TRUE.equals(added)) {
				log.info("매진 랭킹 추가: concertScheduleId={}, soldOutTimestamp={}", 
						concertScheduleId, soldOutTimestamp);
			} else {
				log.debug("매진 랭킹 이미 존재: concertScheduleId={} (최초 매진 시간 유지)", 
						concertScheduleId);
			}
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
	 * 빠른 매진 랭킹 조회 (상위 N개, 점수 및 랭킹 포함)
	 * 
	 * ZRANGE는 이미 정렬된 순서를 반환하므로, 결과의 인덱스로 랭크를 계산합니다.
	 * 이를 통해 N+1 문제를 해결하고 성능을 개선합니다.
	 * 
	 * @param limit 조회할 개수
	 * @return 콘서트 일정 ID, 매진 시간, 랭킹의 쌍 리스트
	 */
	public List<RankingEntry> getTopSoldOutRankingWithScore(int limit) {
		try {
			// ZRANGE: score가 작은 순서대로 조회 (빠른 매진 순서)
			Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
					.rangeWithScores(RANKING_KEY, 0, limit - 1);
			
			if (tuples == null || tuples.isEmpty()) {
				return List.of();
			}
			
			// 인덱스 기반으로 랭크 계산 (0부터 시작하므로 +1)
			// ZRANGE는 이미 정렬된 순서를 반환하므로 인덱스가 랭킹과 동일합니다.
			List<RankingEntry> entries = new java.util.ArrayList<>();
			int index = 0;
			for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
				long rank = index + 1; // 랭킹은 1부터 시작
				entries.add(new RankingEntry(
						Long.parseLong(tuple.getValue().toString()),
						tuple.getScore().longValue(),
						rank
				));
				index++;
			}
			return entries;
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
	 * 랭킹 엔트리 (콘서트 일정 ID, 매진 시간, 랭킹)
	 */
	public static class RankingEntry {
		private final Long concertScheduleId;
		private final Long soldOutTimestamp;
		private final Long rank;

		public RankingEntry(Long concertScheduleId, Long soldOutTimestamp, Long rank) {
			this.concertScheduleId = concertScheduleId;
			this.soldOutTimestamp = soldOutTimestamp;
			this.rank = rank;
		}

		public Long getConcertScheduleId() {
			return concertScheduleId;
		}

		public Long getSoldOutTimestamp() {
			return soldOutTimestamp;
		}

		public Long getRank() {
			return rank;
		}
	}
}
