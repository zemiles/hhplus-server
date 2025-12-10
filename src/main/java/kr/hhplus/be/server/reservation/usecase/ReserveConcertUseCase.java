package kr.hhplus.be.server.reservation.usecase;

import jakarta.transaction.Transactional;
import kr.hhplus.be.server.concert.domain.Seat;
import kr.hhplus.be.server.reservation.domain.Payment;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.port.SeatRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.IllformedLocaleException;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReserveConcertUseCase {

	private final SeatRepositoryPort seatRepositoryPort;
	private final ReservationRepositoryPort reservationRepositoryPort;

	private static final int HOLD_DURATION_MINUTES = 10;

	/**
	 * 좌석 예약 (홀드)
	 *
	 * @param userId 사용자 ID
	 * @param seatId 좌석 ID
	 * @param idempotencyKey 멱등성 키 (중복 요청 방지)
	 * @return 생성된 예약 정보
	 */
	@Transactional
	public Reservation execute(Long userId, Long seatId,String idempotencyKey) {
		// 1. 멱등성 체크 (같은 요청이 중복으로 들어오면 기존 예약 반환)
		if(idempotencyKey != null) {
			Optional<Reservation> byIdempotencyKey = reservationRepositoryPort.findByIdempotencyKey(idempotencyKey);
			if(byIdempotencyKey.isPresent()) {
				return byIdempotencyKey.get();
			}
		}

		// 2. 좌석 조회
		Seat seat = seatRepositoryPort.findById(seatId)
				.orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다. seatId: " + seatId));

		// 3. 좌석 사용 가능 여부 확인
		if(!seatRepositoryPort.isSeatAvailable(seatId)) {
			throw new IllegalArgumentException("이미 예약된 좌석입니다. seatId : " + seatId);
		}

		// 4. 동일 좌석에 대한 활성 예약이 있는지 확인
		if(reservationRepositoryPort.existsBySeatIdAndStatus(seatId, ReservationStatus.HOLD)) {
			throw new IllegalArgumentException("이미 홀드된 좌석입니다. seatId : " + seatId);
		}

		// 5. 예약 생성(비즈니스 로직)
		Reservation reservation = new Reservation();
		reservation.setUserId(userId);
		reservation.setSeat(seat);
		reservation.setConcertSchedule(seat.getConcertSchedule());
		reservation.setStatus(ReservationStatus.HOLD);
		reservation.setHoldExpiresAt(LocalDateTime.now().plusMinutes(HOLD_DURATION_MINUTES));

		// 가격 계산 (센트 단위)
		BigDecimal priceInCents = seat.getConcertSchedule().getConcertPrice()
				.multiply(new BigDecimal(100));

		reservation.setAmountCents(priceInCents);

		reservation.setIdempotencyKey(
				idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString()
		);

		return reservationRepositoryPort.save(reservation);

	}

}
