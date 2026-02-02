package kr.hhplus.be.server.ranking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 빠른 매진 랭킹 응답 DTO
 */
@Getter
@AllArgsConstructor
public class RankingResponse {
	private Long concertScheduleId;
	private Long rank;
	private Long soldOutTimestamp;
	private LocalDateTime soldOutDateTime;

	public RankingResponse(Long concertScheduleId, Long rank, Long soldOutTimestamp) {
		this.concertScheduleId = concertScheduleId;
		this.rank = rank;
		this.soldOutTimestamp = soldOutTimestamp;
		// timestamp를 LocalDateTime으로 변환
		this.soldOutDateTime = LocalDateTime.ofInstant(
				java.time.Instant.ofEpochMilli(soldOutTimestamp),
				ZoneId.systemDefault()
		);
	}
}
