package kr.hhplus.be.server.concert.dto;

import kr.hhplus.be.server.common.CommonResponse;
import kr.hhplus.be.server.concert.common.ConcertStatus;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class ConcertResponse extends CommonResponse {
	private Long concertId;
	private String concertName;
	private ConcertStatus concertStatus;
	private String concertDate;
	private String concertTime;
}
