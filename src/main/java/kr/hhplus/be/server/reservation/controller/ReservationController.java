package kr.hhplus.be.server.reservation.controller;

import kr.hhplus.be.server.reservation.domain.Payment;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.dto.PaymentResponse;
import kr.hhplus.be.server.reservation.dto.ReservationResponse;
import kr.hhplus.be.server.reservation.usecase.ProcessPaymentUseCase;
import kr.hhplus.be.server.reservation.usecase.ReserveConcertUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservation")
@RequiredArgsConstructor
public class ReservationController {

	private final ReserveConcertUseCase reserveConcertUseCase;
	private final ProcessPaymentUseCase processPaymentUseCase;

	/*
	* 좌석 예약 (홀드)
	* POST /api/v1/reservation/dates/place
	* */
	@PostMapping("dates/place")
	public ReservationResponse reservationSeat(@RequestParam Long concertId,
	                                           @RequestParam String date,
	                                           @RequestParam String place,
	                                           @RequestParam Long userId,
	                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		Long seatId = Long.parseLong(place);
		Reservation reservation = reserveConcertUseCase.execute(userId, seatId, idempotencyKey);

		return ReservationResponse.from(reservation);
	}

	/**
	 * 결제 처리
	 * POST /api/v1/user/payment (API 스펙에 따르면 이 경로)
	 */
	@PostMapping("/{reservationId}/payment")
	public PaymentResponse payReservation(
			@PathVariable Long reservationId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

		Payment payment = processPaymentUseCase.execute(reservationId, idempotencyKey);
		return PaymentResponse.from(payment);
	}

}
