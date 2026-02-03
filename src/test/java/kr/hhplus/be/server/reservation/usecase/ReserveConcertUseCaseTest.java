package kr.hhplus.be.server.reservation.usecase;

import kr.hhplus.be.server.common.service.DistributedLockService;
import kr.hhplus.be.server.concert.common.SeatGrade;
import kr.hhplus.be.server.concert.common.SeatStatus;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.Seat;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.port.SeatRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReserveConcertUseCase 단위 테스트
 * 
 * Mock을 사용하여 의존성을 격리하고 비즈니스 로직을 검증합니다.
 * - 정상적인 예약 흐름
 * - 멱등성 키 처리
 * - 좌석 상태 검증
 * - 예외 케이스 처리
 */
@ExtendWith(MockitoExtension.class)
class ReserveConcertUseCaseTest {

	@Mock
	private SeatRepositoryPort seatRepositoryPort;

	@Mock
	private ReservationRepositoryPort reservationRepositoryPort;

	@Mock
	private DistributedLockService distributedLockService;

	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private ReserveConcertUseCase reserveConcertUseCase;

	private Long userId;
	private Long seatId;
	private Seat seat;
	private ConcertSchedule concertSchedule;
	private String idempotencyKey;

	@BeforeEach
	void setUp() {
		userId = 1L;
		seatId = 100L;
		idempotencyKey = "test-idempotency-key";

		// Concert 및 Schedule 설정
		Concert concert = new Concert();
		concert.setConcertName("테스트 콘서트");

		concertSchedule = new ConcertSchedule();
		concertSchedule.setConcertScheduleId(1L);
		concertSchedule.setConcert(concert);
		concertSchedule.setConcertDate("20241225");
		concertSchedule.setConcertTime("180000");
		concertSchedule.setConcertPrice(new BigDecimal(80000));

		// Seat 설정
		seat = new Seat();
		seat.setSeatId(seatId);
		seat.setSeatNumber(1);
		seat.setSeatGrade(SeatGrade.VIP);
		seat.setSeatStatus(SeatStatus.NON_RESERVATION);
		seat.setConcertSchedule(concertSchedule);

		// TransactionTemplate은 실제로 사용되므로 Mock 설정 불필요
	}

	@Test
	@DisplayName("정상적인 좌석 예약 시 예약이 생성됨")
	void testExecute_Success_CreatesReservation() {
		// given
		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			// 분산락 내부에서 실행되는 람다를 실제로 실행
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Reservation> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(seatRepositoryPort.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
		when(reservationRepositoryPort.existsBySeatIdAndStatus(seatId, ReservationStatus.HOLD)).thenReturn(false);
		when(reservationRepositoryPort.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

		Reservation savedReservation = new Reservation();
		savedReservation.setId(1L);
		savedReservation.setUserId(userId);
		savedReservation.setSeat(seat);
		savedReservation.setStatus(ReservationStatus.HOLD);
		when(reservationRepositoryPort.save(any(Reservation.class))).thenReturn(savedReservation);

		// TransactionTemplate Mock 설정
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		doAnswer(invocation -> {
			// 트랜잭션 내부에서 실행되는 람다를 실제로 실행
			org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		}).when(transactionManager).getTransaction(any());

		// when
		Reservation result = reserveConcertUseCase.execute(userId, seatId, null);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(ReservationStatus.HOLD);
		verify(seatRepositoryPort).findByIdWithLock(seatId);
		verify(reservationRepositoryPort).existsBySeatIdAndStatus(seatId, ReservationStatus.HOLD);
		verify(reservationRepositoryPort).save(any(Reservation.class));
	}

	@Test
	@DisplayName("멱등성 키가 있고 기존 예약이 있으면 기존 예약 반환")
	void testExecute_WithIdempotencyKey_ReturnsExistingReservation() {
		// given
		Reservation existingReservation = new Reservation();
		existingReservation.setId(1L);
		existingReservation.setUserId(userId);
		existingReservation.setStatus(ReservationStatus.HOLD);
		existingReservation.setIdempotencyKey(idempotencyKey);

		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Reservation> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(reservationRepositoryPort.findByIdempotencyKey(idempotencyKey))
				.thenReturn(Optional.of(existingReservation));

		// when
		Reservation result = reserveConcertUseCase.execute(userId, seatId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(1L);
		assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);
		verify(reservationRepositoryPort).findByIdempotencyKey(idempotencyKey);
		verify(seatRepositoryPort, never()).findByIdWithLock(any());
		verify(reservationRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("좌석을 찾을 수 없으면 예외 발생")
	void testExecute_SeatNotFound_ThrowsException() {
		// given
		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Reservation> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(seatRepositoryPort.findByIdWithLock(seatId)).thenReturn(Optional.empty());
		when(reservationRepositoryPort.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

		// TransactionTemplate Mock 설정
		doAnswer(invocation -> {
			org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		}).when(transactionManager).getTransaction(any());

		// when & then
		assertThatThrownBy(() -> reserveConcertUseCase.execute(userId, seatId, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("좌석을 찾을 수 없습니다");
	}

	@Test
	@DisplayName("이미 예약된 좌석이면 예외 발생")
	void testExecute_SeatAlreadyReserved_ThrowsException() {
		// given
		seat.setSeatStatus(SeatStatus.RESERVATION);

		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Reservation> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(seatRepositoryPort.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
		when(reservationRepositoryPort.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

		// TransactionTemplate Mock 설정
		doAnswer(invocation -> {
			org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		}).when(transactionManager).getTransaction(any());

		// when & then
		assertThatThrownBy(() -> reserveConcertUseCase.execute(userId, seatId, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 예약된 좌석입니다");
	}

	@Test
	@DisplayName("이미 홀드된 좌석이면 예외 발생")
	void testExecute_SeatAlreadyHeld_ThrowsException() {
		// given
		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Reservation> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(seatRepositoryPort.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
		when(reservationRepositoryPort.existsBySeatIdAndStatus(seatId, ReservationStatus.HOLD)).thenReturn(true);
		when(reservationRepositoryPort.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

		// TransactionTemplate Mock 설정
		doAnswer(invocation -> {
			org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		}).when(transactionManager).getTransaction(any());

		// when & then
		assertThatThrownBy(() -> reserveConcertUseCase.execute(userId, seatId, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 홀드된 좌석입니다");
	}

	@Test
	@DisplayName("예약 생성 시 가격이 올바르게 계산됨")
	void testExecute_PriceCalculation_IsCorrect() {
		// given
		BigDecimal concertPrice = new BigDecimal(80000);
		concertSchedule.setConcertPrice(concertPrice);
		seat.setConcertSchedule(concertSchedule);

		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Reservation> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(seatRepositoryPort.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
		when(reservationRepositoryPort.existsBySeatIdAndStatus(seatId, ReservationStatus.HOLD)).thenReturn(false);
		when(reservationRepositoryPort.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

		Reservation savedReservation = new Reservation();
		savedReservation.setId(1L);
		savedReservation.setUserId(userId);
		savedReservation.setSeat(seat);
		savedReservation.setStatus(ReservationStatus.HOLD);
		savedReservation.setAmountCents(new BigDecimal(8000000)); // 80000원 * 100센트

		when(reservationRepositoryPort.save(any(Reservation.class))).thenAnswer(invocation -> {
			Reservation reservation = invocation.getArgument(0);
			savedReservation.setAmountCents(reservation.getAmountCents());
			return savedReservation;
		});

		// TransactionTemplate Mock 설정
		doAnswer(invocation -> {
			org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		}).when(transactionManager).getTransaction(any());

		// when
		Reservation result = reserveConcertUseCase.execute(userId, seatId, null);

		// then
		assertThat(result.getAmountCents()).isEqualByComparingTo(new BigDecimal(8000000)); // 80000원 * 100센트
	}
}
