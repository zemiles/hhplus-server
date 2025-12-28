package kr.hhplus.be.server.reservation.port;

import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepositoryPort {
	Reservation save(Reservation reservation);
	Optional<Reservation> findById(Long reservationId);
	Optional<Reservation> findByIdempotencyKey(String idempotencyKey);
	boolean existsBySeatIdAndStatus(Long seatId, ReservationStatus reservationStatus);

	List<Reservation> findExpiredReservations(ReservationStatus status, LocalDateTime now);

	int expireReservations(ReservationStatus oldStatus, ReservationStatus newStatus, LocalDateTime now);
}
