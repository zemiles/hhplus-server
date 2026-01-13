package kr.hhplus.be.server.reservation.usecase;

import kr.hhplus.be.server.common.service.DistributedLockService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import kr.hhplus.be.server.concert.common.SeatStatus;
import kr.hhplus.be.server.concert.domain.Seat;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.port.SeatRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReserveConcertUseCase {

	private final SeatRepositoryPort seatRepositoryPort;
	private final ReservationRepositoryPort reservationRepositoryPort;
	private final DistributedLockService distributedLockService;
	private final PlatformTransactionManager transactionManager;
	
	// TransactionTemplate은 PlatformTransactionManager로부터 생성
	private TransactionTemplate getTransactionTemplate() {
		return new TransactionTemplate(transactionManager);
	}

	private static final int HOLD_DURATION_MINUTES = 10;
	private static final String LOCK_KEY_PREFIX = "seat:";

	/**
	 * 좌석 예약 (홀드)
	 * 
	 * 분산락을 사용하여 동시성 제어를 수행합니다.
	 * - 락 키: "seat:{seatId}"
	 * - 락 범위: 좌석 조회부터 예약 생성까지의 전체 과정
	 * 
	 * 주의사항:
	 * - 분산락은 DB 트랜잭션 외부에서 획득되어야 합니다.
	 * - 락을 획득한 후 DB 트랜잭션 내에서 작업을 수행합니다.
	 * - 트랜잭션이 커밋된 후 락이 해제됩니다.
	 *
	 * @param userId 사용자 ID
	 * @param seatId 좌석 ID
	 * @param idempotencyKey 멱등성 키 (중복 요청 방지)
	 * @return 생성된 예약 정보
	 */
	public Reservation execute(Long userId, Long seatId, String idempotencyKey) {
		// 분산락 키 생성: 좌석 ID 기준
		String lockKey = LOCK_KEY_PREFIX + seatId;
		
		// 분산락을 획득하고 작업 실행
		// 락은 트랜잭션 외부에서 획득되지만, 내부 작업은 트랜잭션 내에서 수행됩니다.
		return distributedLockService.executeWithLock(lockKey, () -> {
			// TransactionTemplate을 사용하여 명시적으로 트랜잭션 실행
			return getTransactionTemplate().execute(status -> {
				return executeInternal(userId, seatId, idempotencyKey);
			});
		});
	}

	/**
	 * 좌석 예약 내부 로직 (트랜잭션 내부에서 실행)
	 * 
	 * 분산락 내부에서 실행되므로 새로운 트랜잭션을 시작해야 합니다.
	 *
	 * @param userId 사용자 ID
	 * @param seatId 좌석 ID
	 * @param idempotencyKey 멱등성 키
	 * @return 생성된 예약 정보
	 */
	private Reservation executeInternal(Long userId, Long seatId, String idempotencyKey) {
		// 1. 멱등성 체크 (같은 요청이 중복으로 들어오면 기존 예약 반환)
		if(idempotencyKey != null) {
			Optional<Reservation> byIdempotencyKey = reservationRepositoryPort.findByIdempotencyKey(idempotencyKey);
			if(byIdempotencyKey.isPresent()) {
				return byIdempotencyKey.get();
			}
		}

		// 2. 좌석 조회
		Seat seat = seatRepositoryPort.findByIdWithLock(seatId)
				.orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다. seatId: " + seatId));

		// 3. 좌석 사용 가능 여부 확인
		if(seat.getSeatStatus() != SeatStatus.NON_RESERVATION) {
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
