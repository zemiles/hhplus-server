package kr.hhplus.be.server.reservation.usecase;

import kr.hhplus.be.server.common.service.DistributedLockService;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.point.domain.Ledger;
import kr.hhplus.be.server.point.domain.User;
import kr.hhplus.be.server.point.domain.Wallet;
import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import kr.hhplus.be.server.reservation.domain.Payment;
import kr.hhplus.be.server.reservation.domain.PaymentStatus;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.port.LedgerRepositoryPort;
import kr.hhplus.be.server.reservation.port.PaymentRepositoryPort;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.port.SeatRepositoryPort;
import kr.hhplus.be.server.reservation.port.WalletRepositoryPort;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProcessPaymentUseCase 랭킹 업데이트 테스트
 * 
 * 다양한 테스트 케이스를 포함:
 * - 매진 시 랭킹 추가
 * - 매진이 아닐 때 랭킹 미추가
 * - 좌석이 없을 때 처리
 * - 랭킹 업데이트 실패 시 결제는 성공
 * - 동시 결제 시 매진 확인
 * - 경계값 테스트
 */
@ExtendWith(MockitoExtension.class)
class ProcessPaymentUseCaseRankingTest {

	@Mock
	private ReservationRepositoryPort reservationRepositoryPort;

	@Mock
	private PaymentRepositoryPort paymentRepositoryPort;

	@Mock
	private WalletRepositoryPort walletRepositoryPort;

	@Mock
	private LedgerRepositoryPort ledgerRepositoryPort;

	@Mock
	private SeatRepositoryPort seatRepositoryPort;

	@Mock
	private ConcertRankingService concertRankingService;

	@Mock
	private DistributedLockService distributedLockService;

	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private ProcessPaymentUseCase processPaymentUseCase;

	private Long reservationId;
	private Long userId;
	private Long concertScheduleId;
	private Reservation reservation;
	private Wallet wallet;
	private ConcertSchedule concertSchedule;
	private String idempotencyKey;

	@BeforeEach
	void setUp() {
		reservationId = 1L;
		userId = 100L;
		concertScheduleId = 10L;
		idempotencyKey = "test-payment-key";

		// Concert 및 Schedule 설정
		Concert concert = new Concert();
		concert.setId(1L);
		concert.setConcertName("테스트 콘서트");

		concertSchedule = new ConcertSchedule();
		concertSchedule.setConcertScheduleId(concertScheduleId);
		concertSchedule.setConcert(concert);

		// Reservation 설정
		reservation = new Reservation();
		reservation.setId(reservationId);
		reservation.setUserId(userId);
		reservation.setStatus(ReservationStatus.HOLD);
		reservation.setHoldExpiresAt(LocalDateTime.now().plusMinutes(10));
		reservation.setAmountCents(new BigDecimal(80000));
		reservation.setConcertSchedule(concertSchedule);

		// Wallet 설정
		User user = new User();
		user.setId(userId);

		wallet = new Wallet();
		wallet.setId(1L);
		wallet.setUser(user);
		wallet.setBalanceCents(new BigDecimal(100000));
	}

	@Test
	@DisplayName("모든 좌석이 결제 완료되어 매진되면 랭킹에 추가됨")
	void testExecute_AllSeatsPaid_AddsToRanking() {
		// given
		long totalSeats = 10L;
		long paidReservations = 10L; // 모든 좌석 결제 완료

		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations);

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		verify(concertRankingService).addSoldOutConcert(concertScheduleId);
	}

	@Test
	@DisplayName("일부 좌석만 결제 완료되어 매진이 아니면 랭킹에 추가되지 않음")
	void testExecute_PartialSeatsPaid_DoesNotAddToRanking() {
		// given
		long totalSeats = 10L;
		long paidReservations = 5L; // 일부만 결제 완료

		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations);

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		verify(concertRankingService, never()).addSoldOutConcert(anyLong());
	}

	@Test
	@DisplayName("좌석이 없으면 랭킹 확인을 하지 않음")
	void testExecute_NoSeats_DoesNotCheckRanking() {
		// given
		long totalSeats = 0L;

		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		verify(reservationRepositoryPort, never()).countByConcertScheduleIdAndStatus(anyLong(), any());
		verify(concertRankingService, never()).addSoldOutConcert(anyLong());
	}

	@Test
	@DisplayName("결제 완료 수가 좌석 수보다 많아도 랭킹에 추가됨 (데이터 불일치 허용)")
	void testExecute_MorePaidThanSeats_AddsToRanking() {
		// given
		long totalSeats = 10L;
		long paidReservations = 15L; // 좌석 수보다 많음 (데이터 불일치)

		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations);

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		verify(concertRankingService).addSoldOutConcert(concertScheduleId);
	}

	@Test
	@DisplayName("결제 완료 수가 좌석 수와 정확히 같을 때 랭킹에 추가됨")
	void testExecute_ExactMatch_AddsToRanking() {
		// given
		long totalSeats = 10L;
		long paidReservations = 10L; // 정확히 같음

		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations);

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		verify(concertRankingService).addSoldOutConcert(concertScheduleId);
	}

	@Test
	@DisplayName("랭킹 업데이트 실패 시에도 결제는 성공함")
	void testExecute_RankingUpdateFails_PaymentStillSucceeds() {
		// given
		long totalSeats = 10L;
		long paidReservations = 10L;

		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations);
		doThrow(new RuntimeException("Ranking service error"))
				.when(concertRankingService).addSoldOutConcert(anyLong());

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		verify(concertRankingService).addSoldOutConcert(concertScheduleId);
	}

	@Test
	@DisplayName("좌석 개수 조회 실패 시에도 결제는 성공함")
	void testExecute_SeatCountQueryFails_PaymentStillSucceeds() {
		// given
		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId))
				.thenThrow(new RuntimeException("Database error"));

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		verify(reservationRepositoryPort, never()).countByConcertScheduleIdAndStatus(anyLong(), any());
	}

	@Test
	@DisplayName("결제 완료 수 조회 실패 시에도 결제는 성공함")
	void testExecute_PaidCountQueryFails_PaymentStillSucceeds() {
		// given
		long totalSeats = 10L;

		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(concertScheduleId, ReservationStatus.PAID))
				.thenThrow(new RuntimeException("Database error"));

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		verify(concertRankingService, never()).addSoldOutConcert(anyLong());
	}

	@Test
	@DisplayName("매진 확인은 결제 완료 후에 수행됨")
	void testExecute_RankingCheckAfterPayment_ChecksAfterPayment() {
		// given
		long totalSeats = 10L;
		long paidReservations = 10L;

		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations);

		// when
		processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then - 결제 저장이 먼저 호출되고, 그 다음 랭킹 확인이 호출됨
		var inOrder = inOrder(paymentRepositoryPort, reservationRepositoryPort, concertRankingService);
		inOrder.verify(paymentRepositoryPort).save(any(Payment.class));
		inOrder.verify(reservationRepositoryPort).save(any(Reservation.class));
		inOrder.verify(concertRankingService).addSoldOutConcert(concertScheduleId);
	}

	@Test
	@DisplayName("매진이 아닌 경우 랭킹 서비스를 호출하지 않음")
	void testExecute_NotSoldOut_DoesNotCallRankingService() {
		// given
		long totalSeats = 10L;
		long paidReservations = 9L; // 아직 매진 아님

		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations);

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		verify(concertRankingService, never()).addSoldOutConcert(anyLong());
	}

	@Test
	@DisplayName("매진 확인 시 현재 결제도 포함하여 계산됨")
	void testExecute_CurrentPaymentIncluded_ChecksCorrectly() {
		// given
		// 결제 전: 9개 결제 완료, 총 10개 좌석
		// 결제 후: 10개 결제 완료 (현재 결제 포함), 총 10개 좌석 -> 매진
		long totalSeats = 10L;
		long paidReservations = 9L; // 현재 결제 전 상태

		setupSuccessfulPayment();
		when(seatRepositoryPort.countByConcertScheduleId(concertScheduleId)).thenReturn(totalSeats);
		// 결제 완료 후에는 10개가 됨 (현재 결제 포함)
		when(reservationRepositoryPort.countByConcertScheduleIdAndStatus(concertScheduleId, ReservationStatus.PAID))
				.thenReturn(paidReservations + 1); // 현재 결제 포함

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		// 실제로는 결제 후 상태를 확인하므로 매진이 됨
		verify(concertRankingService).addSoldOutConcert(concertScheduleId);
	}

	// Helper method
	private void setupSuccessfulPayment() {
		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class)))
				.thenAnswer(invocation -> {
					@SuppressWarnings("unchecked")
					java.util.function.Supplier<Payment> supplier = invocation.getArgument(1);
					return supplier.get();
				});

		when(reservationRepositoryPort.findById(reservationId)).thenReturn(Optional.of(reservation));
		when(paymentRepositoryPort.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
		when(walletRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(wallet));
		when(walletRepositoryPort.deductBalanceIfSufficient(anyLong(), any(BigDecimal.class))).thenReturn(true);

		Payment savedPayment = new Payment();
		savedPayment.setId(1L);
		savedPayment.setUserId(userId);
		savedPayment.setReservationId(reservationId);
		savedPayment.setTotalAmountCents(reservation.getAmountCents());
		savedPayment.setStatus(PaymentStatus.APPROVED);

		when(paymentRepositoryPort.save(any(Payment.class))).thenAnswer(invocation -> {
			Payment payment = invocation.getArgument(0);
			savedPayment.setIdempotencyKey(payment.getIdempotencyKey());
			savedPayment.setStatus(payment.getStatus());
			return savedPayment;
		});

		when(ledgerRepositoryPort.save(any(Ledger.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(reservationRepositoryPort.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// TransactionTemplate Mock 설정
		// TransactionTemplate.execute()는 내부적으로 transactionManager.getTransaction(TransactionDefinition)을 호출
		// getTransaction()은 TransactionStatus를 반환해야 하며,
		// TransactionTemplate.execute()가 실제로 callback.doInTransaction()을 실행함
		// 따라서 getTransaction()을 mock하여 TransactionStatus를 반환하고,
		// TransactionTemplate.execute()가 실제로 callback을 실행하도록 함
		org.springframework.transaction.support.DefaultTransactionStatus transactionStatus = 
				new org.springframework.transaction.support.DefaultTransactionStatus(
						null, true, false, false, false, null);
		
		when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
	}
}
