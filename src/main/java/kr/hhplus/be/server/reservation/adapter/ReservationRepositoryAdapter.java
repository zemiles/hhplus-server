package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReservationRepositoryAdapter implements ReservationRepositoryPort {
	@Override
	public Reservation save(Reservation reservation) {
		return reservation;
	}

	@Override
	public Optional<Reservation> findById(Long reservationId) {
		return Optional.empty();
	}

	@Override
	public Optional<Reservation> findByIdempotencyKey(String idempotencyKey) {
		return Optional.empty();
	}

	@Override
	public boolean existsBySeatIdAndStatus(Long seatId, ReservationStatus reservationStatus) {
		return false;
	}
}
