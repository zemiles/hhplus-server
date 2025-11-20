package kr.hhplus.be.server.point.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatus {
	NORMAL("1", "사용중"),
	STOP("2", "중지");

	private String code;
	private String dec;

}
