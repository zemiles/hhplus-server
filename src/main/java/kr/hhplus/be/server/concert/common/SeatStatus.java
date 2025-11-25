package kr.hhplus.be.server.concert.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public enum SeatStatus {
	RESERVATION("1", "예약중"),
	NON_RESERVATION("2", "예약하지않음");

	private String code;
	private String dec;

	SeatStatus(String code, String dec) {
		this.code = code;
		this.dec = dec;
	}
}
