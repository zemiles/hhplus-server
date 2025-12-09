package kr.hhplus.be.server.reservation.domain;

import lombok.Getter;

public enum PaymentStatus {
	INIT(1, "초기화"),
	APPROVED(2, "승인"),
	FAILED(3, "실패"),
	CANCELLED(4, "취소"),
	PARTIAL(5, "부분결제");

	@Getter
	private final int code;

	@Getter
	private final String description;

	PaymentStatus(int code, String description) {
		this.code = code;
		this.description = description;
	}
}
