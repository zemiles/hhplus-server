package kr.hhplus.be.server.concert.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public enum SeatGrade {
	VIP("1", "VIP좌석"),
	ROYAL("2", "R석"),
	SPECIAL("3", "S석"),
	A_GRADE("4", "A석");


	private String code;
	private String dec;

	SeatGrade(String code, String dec) {
		this.code = code;
		this.dec = dec;
	}
}
