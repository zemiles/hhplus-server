package kr.hhplus.be.server.reservation.repository;

import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReservationJpaRepository extends JpaRepository<Reservation, Long> {
	Optional<Reservation> findByIdempotencyKey(String idempotencyKey);
	
	@Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.seat.seatId = :seatId AND r.status = :status")
	boolean existsBySeatIdAndStatus(@Param("seatId") Long seatId, @Param("status") ReservationStatus status);
}
