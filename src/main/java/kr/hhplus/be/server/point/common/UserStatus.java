package kr.hhplus.be.server.point.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public enum UserStatus {
	NORMAL("1", "사용중"),
	STOP("2", "중지");

	private String code;
	private String dec;

	UserStatus(String code, String dec) {
		this.code = code;
		this.dec = dec;
	}
}
