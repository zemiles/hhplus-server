package kr.hhplus.be.server.reservation.listener;

import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.event.PaymentCompletedEvent;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.port.SeatRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * PaymentRankingEventListener 단위 테스트
 * 
 * 결제 완료 이벤트를 수신하여 랭킹을 업데이트하는 리스너를 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentRankingEventListenerTest {

	@Mock
	private SeatRepositoryPort seatRepositoryPort;

	@Mock
	private ReservationRepositoryPort reservationRepositoryPort;

	@Mock
	private ConcertRankingService concertRankingService;

	@InjectMocks
	private PaymentRankingEventListener listener;

	private PaymentCompletedEvent event;
	private Long concertScheduleId;
	private Long paymentId;

	@BeforeEach
	void setUp() {
		concertScheduleId = 1L;
		paymentId = 100L;

		event = new PaymentCompletedEvent(
				this,
				paymentId,
				200L, // userId
				300L, // reservationId
				concertScheduleId,
				new BigDecimal(80000),
				"test-idempotency-key"
		);
	}

	@Test
	@DisplayName("매진된 콘서트는 랭킹에 추가됨")
	void testHandlePaymentCompleted_WhenSoldOut_AddsToRanking() throws InterruptedException {
		// given
		long totalSeats = 100L;
		long paidReservations = 100L; // 매진

		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(
				concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations);

		// when
		listener.handlePaymentCompleted(event);

		// 비동기 처리를 위해 잠시 대기
		Thread.sleep(100);

		// then
		verify(seatRepositoryPort).countByConcertScheduleId(concertScheduleId);
		verify(reservationRepositoryPort).countByConcertScheduleIdAndStatus(
				concertScheduleId, ReservationStatus.PAID);
		verify(concertRankingService).addSoldOutConcert(concertScheduleId);
	}

	@Test
	@DisplayName("매진되지 않은 콘서트는 랭킹에 추가되지 않음")
	void testHandlePaymentCompleted_WhenNotSoldOut_DoesNotAddToRanking() throws InterruptedException {
		// given
		long totalSeats = 100L;
		long paidReservations = 50L; // 매진 아님

		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(
				concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations);

		// when
		listener.handlePaymentCompleted(event);

		// 비동기 처리를 위해 잠시 대기
		Thread.sleep(100);

		// then
		verify(seatRepositoryPort).countByConcertScheduleId(concertScheduleId);
		verify(reservationRepositoryPort).countByConcertScheduleIdAndStatus(
				concertScheduleId, ReservationStatus.PAID);
		verify(concertRankingService, never()).addSoldOutConcert(anyLong());
	}

	@Test
	@DisplayName("좌석이 없으면 매진 확인을 하지 않음")
	void testHandlePaymentCompleted_WhenNoSeats_DoesNotCheckSoldOut() throws InterruptedException {
		// given
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(0L);

		// when
		listener.handlePaymentCompleted(event);

		// 비동기 처리를 위해 잠시 대기
		Thread.sleep(100);

		// then
		verify(seatRepositoryPort).countByConcertScheduleId(concertScheduleId);
		verify(reservationRepositoryPort, never()).countByConcertScheduleIdAndStatus(anyLong(), any());
		verify(concertRankingService, never()).addSoldOutConcert(anyLong());
	}

	@Test
	@DisplayName("랭킹 업데이트 실패 시 예외를 던지지 않음")
	void testHandlePaymentCompleted_WhenRankingUpdateFails_DoesNotThrowException() throws InterruptedException {
		// given
		long totalSeats = 100L;
		long paidReservations = 100L;

		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(
				concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations);
		doThrow(new RuntimeException("랭킹 업데이트 실패")).when(concertRankingService)
				.addSoldOutConcert(concertScheduleId);

		// when & then - 예외가 발생하지 않아야 함
		listener.handlePaymentCompleted(event);

		// 비동기 처리를 위해 잠시 대기
		Thread.sleep(100);

		verify(concertRankingService).addSoldOutConcert(concertScheduleId);
	}
}
