package kr.hhplus.be.server.reservation.dto;

import kr.hhplus.be.server.common.CommonResponse;
import kr.hhplus.be.server.reservation.domain.Payment;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentResponse extends CommonResponse {
	private Long paymentId;
	private Long reservationId;
	private BigDecimal amountCents;
	private String status;
	private String approvedAt;

	public static PaymentResponse from(Payment payment) {
		PaymentResponse response = new PaymentResponse();
		response.setPaymentId(payment.getId());
		response.setReservationId(payment.getReservationId());
		response.setAmountCents(payment.getTotalAmountCents());
		response.setStatus(payment.getStatus().name());
		response.setApprovedAt(payment.getApprovedAt() != null ?
				payment.getApprovedAt().toString() : null);
		return response;
	}

}
