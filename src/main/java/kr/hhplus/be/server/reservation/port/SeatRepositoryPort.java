package kr.hhplus.be.server.reservation.port;

import kr.hhplus.be.server.concert.domain.Seat;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeatRepositoryPort {
	Optional<Seat> findById(Long seatId);
	Optional<Seat> findByIdWithLock(Long seatId);
	Seat save(Seat seat);
	boolean isSeatAvailable(Long seatId);
	
	/**
	 * 콘서트 일정별 전체 좌석 개수 조회
	 */
	long countByConcertScheduleId(Long concertScheduleId);
}
