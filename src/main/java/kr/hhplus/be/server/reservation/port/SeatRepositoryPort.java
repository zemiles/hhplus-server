package kr.hhplus.be.server.reservation.port;

import kr.hhplus.be.server.concert.domain.Seat;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeatRepositoryPort {
	Optional<Seat> findById(Long seatId);
	Seat save(Seat seat);
	boolean isSeatAvailable(Long seatId);
}
