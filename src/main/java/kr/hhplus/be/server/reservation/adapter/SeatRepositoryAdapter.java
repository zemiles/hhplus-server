package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.concert.common.SeatStatus;
import kr.hhplus.be.server.concert.domain.Seat;
import kr.hhplus.be.server.concert.repository.SeatRepository;
import kr.hhplus.be.server.reservation.port.SeatRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SeatRepositoryAdapter implements SeatRepositoryPort {

	private final SeatRepository seatRepository;

	@Override
	public Optional<Seat> findById(Long seatId) {
		return seatRepository.findById(seatId);
	}

	@Override
	public Optional<Seat> findByIdWithLock(Long seatId) {
		return seatRepository.findByIdWithLock(seatId);
	}

	@Override
	public Seat save(Seat seat) {
		return seatRepository.save(seat);
	}

	@Override
	public boolean isSeatAvailable(Long seatId) {
		return seatRepository.findById(seatId)
				.map(seat -> seat.getSeatStatus() == SeatStatus.NON_RESERVATION)
				.orElse(false);
	}
}
