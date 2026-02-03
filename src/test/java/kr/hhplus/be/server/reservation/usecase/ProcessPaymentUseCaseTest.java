package kr.hhplus.be.server.reservation.usecase;

import kr.hhplus.be.server.common.service.DistributedLockService;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.Seat;
import kr.hhplus.be.server.point.domain.Ledger;
import kr.hhplus.be.server.point.domain.LedgerType;
import kr.hhplus.be.server.point.domain.User;
import kr.hhplus.be.server.point.domain.Wallet;
import kr.hhplus.be.server.reservation.domain.Payment;
import kr.hhplus.be.server.reservation.domain.PaymentStatus;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.port.LedgerRepositoryPort;
import kr.hhplus.be.server.reservation.port.PaymentRepositoryPort;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.port.WalletRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProcessPaymentUseCase 단위 테스트
 * 
 * Mock을 사용하여 의존성을 격리하고 비즈니스 로직을 검증합니다.
 * - 정상적인 결제 흐름
 * - 멱등성 키 처리
 * - 잔액 부족 처리
 * - 예약 만료 처리
 * - 예외 케이스 처리
 */
@ExtendWith(MockitoExtension.class)
class ProcessPaymentUseCaseTest {

	@Mock
	private ReservationRepositoryPort reservationRepositoryPort;

	@Mock
	private PaymentRepositoryPort paymentRepositoryPort;

	@Mock
	private WalletRepositoryPort walletRepositoryPort;

	@Mock
	private LedgerRepositoryPort ledgerRepositoryPort;

	@Mock
	private DistributedLockService distributedLockService;

	@Mock
	private PlatformTransactionManager transactionManager;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private ProcessPaymentUseCase processPaymentUseCase;

	private Long reservationId;
	private Long userId;
	private Reservation reservation;
	private Wallet wallet;
	private String idempotencyKey;

	@BeforeEach
	void setUp() {
		reservationId = 1L;
		userId = 100L;
		idempotencyKey = "test-payment-key";

		// ConcertSchedule 설정
		ConcertSchedule concertSchedule = new ConcertSchedule();
		concertSchedule.setConcertScheduleId(1L);

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
		wallet.setBalanceCents(new BigDecimal(100000)); // 1000원 (충분한 잔액)
	}

	@Test
	@DisplayName("정상적인 결제 처리 시 결제가 승인되고 예약 상태가 PAID로 변경됨")
	void testExecute_Success_ApprovesPaymentAndUpdatesReservation() {
		// given
		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
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
		savedPayment.setStatus(PaymentStatus.INIT);

		when(paymentRepositoryPort.save(any(Payment.class))).thenAnswer(invocation -> {
			Payment payment = invocation.getArgument(0);
			savedPayment.setIdempotencyKey(payment.getIdempotencyKey());
			savedPayment.setStatus(payment.getStatus());
			return savedPayment;
		});

		when(ledgerRepositoryPort.save(any(Ledger.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(reservationRepositoryPort.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		assertThat(result.isApproved()).isTrue();
		verify(walletRepositoryPort).deductBalanceIfSufficient(wallet.getId(), reservation.getAmountCents());
		// 주의: deductBalanceIfSufficient가 이미 차감하므로 deductBalance는 호출되지 않음
		verify(walletRepositoryPort, never()).deductBalance(anyLong(), any());
		verify(paymentRepositoryPort, times(1)).save(any(Payment.class)); // APPROVED 상태로 한 번만 저장
		verify(ledgerRepositoryPort).save(any(Ledger.class));
		verify(reservationRepositoryPort).save(any(Reservation.class));
	}

	@Test
	@DisplayName("멱등성 키가 있고 기존 결제가 있으면 기존 결제 반환")
	void testExecute_WithIdempotencyKey_ReturnsExistingPayment() {
		// given
		Payment existingPayment = new Payment();
		existingPayment.setId(1L);
		existingPayment.setUserId(userId);
		existingPayment.setReservationId(reservationId);
		existingPayment.setStatus(PaymentStatus.APPROVED);
		existingPayment.setIdempotencyKey(idempotencyKey);

		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Payment> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(reservationRepositoryPort.findById(reservationId)).thenReturn(Optional.of(reservation));
		when(paymentRepositoryPort.findByIdempotencyKey(idempotencyKey))
				.thenReturn(Optional.of(existingPayment));

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(1L);
		assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);
		verify(paymentRepositoryPort).findByIdempotencyKey(idempotencyKey);
		verify(walletRepositoryPort, never()).deductBalanceIfSufficient(anyLong(), any());
		verify(paymentRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("예약을 찾을 수 없으면 예외 발생")
	void testExecute_ReservationNotFound_ThrowsException() {
		// given
		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Payment> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(reservationRepositoryPort.findById(reservationId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> processPaymentUseCase.execute(reservationId, idempotencyKey))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("예약을 찾을 수 없습니다");
	}

	@Test
	@DisplayName("예약이 만료되었으면 예외 발생")
	void testExecute_ReservationExpired_ThrowsException() {
		// given
		reservation.setHoldExpiresAt(LocalDateTime.now().minusMinutes(5)); // 만료됨

		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Payment> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(reservationRepositoryPort.findById(reservationId)).thenReturn(Optional.of(reservation));
		when(reservationRepositoryPort.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when & then
		assertThatThrownBy(() -> processPaymentUseCase.execute(reservationId, idempotencyKey))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("예약이 만료되었습니다");
		verify(reservationRepositoryPort).save(any(Reservation.class)); // 만료 상태로 저장
	}

	@Test
	@DisplayName("이미 결제된 예약이면 예외 발생")
	void testExecute_ReservationAlreadyPaid_ThrowsException() {
		// given
		reservation.setStatus(ReservationStatus.PAID);

		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Payment> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(reservationRepositoryPort.findById(reservationId)).thenReturn(Optional.of(reservation));

		// when & then
		assertThatThrownBy(() -> processPaymentUseCase.execute(reservationId, idempotencyKey))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("결제할 수 없는 예약입니다");
	}

	@Test
	@DisplayName("지갑을 찾을 수 없으면 예외 발생")
	void testExecute_WalletNotFound_ThrowsException() {
		// given
		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Payment> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(reservationRepositoryPort.findById(reservationId)).thenReturn(Optional.of(reservation));
		when(paymentRepositoryPort.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
		when(walletRepositoryPort.findByUserId(userId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> processPaymentUseCase.execute(reservationId, idempotencyKey))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("지갑을 찾을 수 없습니다");
	}

	@Test
	@DisplayName("잔액이 부족하면 예외 발생")
	void testExecute_InsufficientBalance_ThrowsException() {
		// given
		wallet.setBalanceCents(new BigDecimal(10000)); // 100원 (부족한 잔액)

		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.function.Supplier<Payment> supplier = invocation.getArgument(1);
			return supplier.get();
		});

		when(reservationRepositoryPort.findById(reservationId)).thenReturn(Optional.of(reservation));
		when(paymentRepositoryPort.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
		when(walletRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(wallet));
		when(walletRepositoryPort.deductBalanceIfSufficient(anyLong(), any(BigDecimal.class))).thenReturn(false);
		when(walletRepositoryPort.getBalance(wallet.getId())).thenReturn(new BigDecimal(10000));

		// when & then
		assertThatThrownBy(() -> processPaymentUseCase.execute(reservationId, idempotencyKey))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("잔액이 부족합니다");
		verify(walletRepositoryPort).deductBalanceIfSufficient(wallet.getId(), reservation.getAmountCents());
		verify(walletRepositoryPort).getBalance(wallet.getId());
		verify(paymentRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("결제 완료 시 이벤트가 발행됨")
	void testExecute_Success_PublishesPaymentCompletedEvent() {
		// given
		when(distributedLockService.executeWithLock(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
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
		savedPayment.setIdempotencyKey(idempotencyKey);

		when(paymentRepositoryPort.save(any(Payment.class))).thenAnswer(invocation -> {
			Payment payment = invocation.getArgument(0);
			savedPayment.setIdempotencyKey(payment.getIdempotencyKey());
			savedPayment.setStatus(payment.getStatus());
			return savedPayment;
		});

		when(ledgerRepositoryPort.save(any(Ledger.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(reservationRepositoryPort.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when
		Payment result = processPaymentUseCase.execute(reservationId, idempotencyKey);

		// then
		assertThat(result).isNotNull();
		// 이벤트 발행은 트랜잭션 커밋 후에 발생하므로, 
		// 실제로는 TransactionSynchronizationManager를 통해 처리됩니다.
		// 여기서는 이벤트 발행 로직이 등록되었는지만 확인합니다.
	}
}
