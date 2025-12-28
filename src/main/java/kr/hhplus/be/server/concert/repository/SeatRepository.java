package kr.hhplus.be.server.concert.repository;

import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.concert.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
	/**
	 * SELECT FOR UPDATE로 좌석을 조회하여 행 잠금
	 * 다른 트랜잭션은 이 좌석을 조회할 때 대기하게 됨
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Seat s WHERE s.seatId = :seatId")
	Optional<Seat> findByIdWithLock(@Param("seatId") Long seatId);

}
