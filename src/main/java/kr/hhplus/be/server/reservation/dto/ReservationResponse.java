package kr.hhplus.be.server.reservation.dto;

import kr.hhplus.be.server.common.CommonResponse;
import kr.hhplus.be.server.reservation.domain.Reservation;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ReservationResponse extends CommonResponse {
	private Long reservationId;
	private Long userId;
	private Long seatId;
	private String status;
	private BigDecimal amountCents;
	private String holdExpiresAt;

	public static ReservationResponse from(Reservation reservation) {
		ReservationResponse reservationResponse = new ReservationResponse();
		reservationResponse.setReservationId(reservation.getId());
		reservationResponse.setUserId(reservation.getUserId());
		reservationResponse.setSeatId(reservation.getSeat().getSeatId());
		reservationResponse.setStatus(reservation.getStatus().name());
		reservationResponse.setAmountCents(reservation.getAmountCents());
		reservationResponse.setHoldExpiresAt(reservation.getHoldExpiresAt() != null
		? reservationResponse.getHoldExpiresAt()
				: null);

		return reservationResponse;
	}

}
