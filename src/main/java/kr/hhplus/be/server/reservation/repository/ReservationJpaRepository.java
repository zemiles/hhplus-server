package kr.hhplus.be.server.reservation.repository;

import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationJpaRepository extends JpaRepository<Reservation, Long> {
	Optional<Reservation> findByIdempotencyKey(String idempotencyKey);
	
	@Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.seat.seatId = :seatId AND r.status = :status")
	boolean existsBySeatIdAndStatus(@Param("seatId") Long seatId, @Param("status") ReservationStatus status);

	@Query("SELECT r FROM Reservation r " +
		"WHERE r.status = :status AND r.holdExpiresAt < :now")
	List<Reservation> findExpiredReservations(
			@Param("status") ReservationStatus status,
			@Param("now")LocalDateTime now
			);

	@Modifying
	@Query("UPDATE Reservation r SET r.status = :newStatus " +
		"WHERE r.status = :oldStatus AND r.holdExpiresAt < :now")
		int expireReservations(@Param("oldStatus") ReservationStatus oldStatus,
		                       @Param("newStatus") ReservationStatus newStatus,
		                       @Param("now") LocalDateTime now);

	/**
	 * 콘서트 일정별 결제 완료된 예약 개수 조회
	 */
	@Query("SELECT COUNT(r) FROM Reservation r WHERE r.concertSchedule.concertScheduleId = :concertScheduleId AND r.status = :status")
	long countByConcertScheduleIdAndStatus(@Param("concertScheduleId") Long concertScheduleId, @Param("status") ReservationStatus status);
}
