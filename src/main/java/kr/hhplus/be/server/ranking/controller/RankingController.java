package kr.hhplus.be.server.ranking.controller;

import kr.hhplus.be.server.ranking.dto.RankingResponse;
import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 빠른 매진 랭킹 조회 API
 */
@RestController
@RequestMapping("/api/v1/ranking")
@RequiredArgsConstructor
public class RankingController {

	private final ConcertRankingService concertRankingService;

	/**
	 * 빠른 매진 랭킹 조회 (상위 N개)
	 * 
	 * @param limit 조회할 개수 (기본값: 10)
	 * @return 빠른 매진 랭킹 리스트
	 */
	@GetMapping("/soldout")
	public List<RankingResponse> getTopSoldOutRanking(
			@RequestParam(defaultValue = "10") int limit) {
		
		List<ConcertRankingService.RankingEntry> rankingEntries = 
				concertRankingService.getTopSoldOutRankingWithScore(limit);

		return rankingEntries.stream()
				.map(entry -> {
					long rank = concertRankingService.getRank(entry.getConcertScheduleId());
					return new RankingResponse(
							entry.getConcertScheduleId(),
							rank,
							entry.getSoldOutTimestamp()
					);
				})
				.collect(Collectors.toList());
	}
}
