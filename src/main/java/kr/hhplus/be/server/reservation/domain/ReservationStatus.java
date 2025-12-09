package kr.hhplus.be.server.reservation.domain;

public enum ReservationStatus {
	PENDING(1, "대기"),
	HOLD(2, "홀드"),
	EXPIRED(3, "만료"),
	CANCELLED(4, "취소"),
	PAID(5, "결제완료")
	;

	private final int code;
	private final String description;

	ReservationStatus(int code, String description) {
		this.code = code;
		this.description = description;
	}

	public int getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

}
