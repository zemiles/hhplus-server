package kr.hhplus.be.server.concert.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public enum ConcertStatus {
	CLOSE("1", "공연종료"),
	OPEN("2", "공연중"),
	STOP("3", "공연중지"),
	RESERVATION("4", "예약중");

	private final String code;
	private final String dec;

	ConcertStatus(String code, String dec) {
		this.code = code;
		this.dec = dec;
	}

}
