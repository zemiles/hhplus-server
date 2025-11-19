package kr.hhplus.be.server.concert.common;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum ConcertStatus {
	COLSE("1", "공연종료"),
	OPEN("2", "공연중"),
	STOP("3", "공연중지");

	private String code;
	private String dec;
}
