package kr.hhplus.be.server.concert.dto;

import kr.hhplus.be.server.common.CommonResponse;
import kr.hhplus.be.server.concert.common.ConcertStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConcertResponse extends CommonResponse {
	private Long concertId;
	private String concertName;
	private ConcertStatus concertStatus;
	private String concertDate;
	private String concertTime;

	public ConcertResponse() {}

	public ConcertResponse(Long concertId, String concertName, ConcertStatus concertStatus, String concertDate, String concertTime) {
		this.concertId = concertId;
		this.concertName = concertName;
		this.concertStatus = concertStatus;
		this.concertDate = concertDate;
		this.concertTime = concertTime;
	}
}
