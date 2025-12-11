package kr.hhplus.be.server.point.domain;

import lombok.Getter;

public enum LedgerType {
	CHARGE(1, "충전"),
	PAYMENT(2, "결제"),
	REFUND(3, "환불"),
	CANCEL(4, "취소"),
	ADJUST(5, "조정");

	@Getter
	private final int type;

	@Getter
	private final String dec;


	LedgerType(int type, String dec) {
		this.type = type;
		this.dec = dec;
	}

}
