package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.repository.ReservationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReservationRepositoryAdapter implements ReservationRepositoryPort {

	private final ReservationJpaRepository reservationJpaRepository;

	@Override
	public Reservation save(Reservation reservation) {
		return reservationJpaRepository.save(reservation);
	}

	@Override
	public Optional<Reservation> findById(Long reservationId) {
		return reservationJpaRepository.findById(reservationId);
	}

	@Override
	public Optional<Reservation> findByIdempotencyKey(String idempotencyKey) {
		return reservationJpaRepository.findByIdempotencyKey(idempotencyKey);
	}

	@Override
	public boolean existsBySeatIdAndStatus(Long seatId, ReservationStatus reservationStatus) {
		return reservationJpaRepository.existsBySeatIdAndStatus(seatId, reservationStatus);
	}
}
