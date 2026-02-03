package kr.hhplus.be.server.reservation.usecase;

import jakarta.persistence.EntityManager;
import kr.hhplus.be.server.concert.common.ConcertStatus;
import kr.hhplus.be.server.concert.common.SeatGrade;
import kr.hhplus.be.server.concert.common.SeatStatus;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.Seat;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
import kr.hhplus.be.server.concert.repository.ConcertScheduleRepository;
import kr.hhplus.be.server.concert.repository.SeatRepository;
import kr.hhplus.be.server.point.domain.User;
import kr.hhplus.be.server.point.domain.Wallet;
import kr.hhplus.be.server.point.common.UserStatus;
import kr.hhplus.be.server.point.repository.WalletRepository;
import kr.hhplus.be.server.reservation.domain.Payment;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.repository.PaymentJpaRepository;
import kr.hhplus.be.server.reservation.repository.ReservationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분산락 통합 테스트
 * 
 * 테스트 목적:
 * - Redis 기반 분산락이 제대로 동작하는지 검증
 * - 분산 환경에서 동시성 제어가 올바르게 수행되는지 확인
 * - DB 트랜잭션과 분산락이 함께 사용될 때의 동작 검증
 * 
 * 테스트 시나리오:
 * 1. 좌석 예약 시 분산락 적용 검증
 * 2. 결제 처리 시 분산락 적용 검증
 * 3. 동시 요청 시 1건만 성공하는지 확인
 */
@SpringBootTest
@ActiveProfiles("h2")
class DistributedLockIntegrationTest {

	@Autowired
	private ReserveConcertUseCase reserveConcertUseCase;

	@Autowired
	private ProcessPaymentUseCase processPaymentUseCase;

	@Autowired
	private ConcertRepository concertRepository;

	@Autowired
	private ConcertScheduleRepository concertScheduleRepository;

	@Autowired
	private SeatRepository seatRepository;

	@Autowired
	private ReservationJpaRepository reservationJpaRepository;

	@Autowired
	private PaymentJpaRepository paymentJpaRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private Long seatId;
	private Long concertScheduleId;
	private Long userId;

	@BeforeEach
	void setUp() {
		// TransactionTemplate을 사용하여 명시적으로 트랜잭션 관리
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.execute(status -> {
			// 사용자 및 지갑 생성
			User user = new User();
			user.setUserName("테스트 사용자");
			user.setUserEmail("test@test.com");
			user.setUserTel("01012345678");
			user.setUserStatus(UserStatus.NORMAL);
			// User는 Repository가 없으므로 EntityManager를 사용
			entityManager.persist(user);
			entityManager.flush();
			userId = user.getId();
			return null;
		});

		transactionTemplate.execute(status -> {
			// User를 다시 조회 (트랜잭션이 다르므로)
			User user = entityManager.find(User.class, userId);
			
			Wallet wallet = new Wallet();
			wallet.setUser(user);
			wallet.setBalanceCents(new BigDecimal(1000000)); // 10000원
			wallet.setCurrency("KRW");
			wallet = walletRepository.save(wallet);

			// 콘서트 및 좌석 생성
			Concert concert = new Concert();
			concert.setConcertName("분산락 테스트 콘서트");
			concert.setConcertDec("분산락 통합 테스트용 콘서트");
			concert.setConcertStatus(ConcertStatus.RESERVATION);
			concert = concertRepository.save(concert);

			ConcertSchedule schedule = new ConcertSchedule();
			schedule.setConcert(concert);
			schedule.setConcertDate("20241225");
			schedule.setConcertTime("180000");
			schedule.setConcertPrice(new BigDecimal(80000));
			schedule = concertScheduleRepository.save(schedule);
			concertScheduleId = schedule.getConcertScheduleId();

			Seat seat = new Seat();
			seat.setSeatNumber(1);
			seat.setSeatGrade(SeatGrade.VIP);
			seat.setSeatStatus(SeatStatus.NON_RESERVATION);
			seat.setConcertSchedule(schedule);
			seat = seatRepository.save(seat);
			seatId = seat.getSeatId();

			entityManager.flush();
			entityManager.clear();
			return null;
		});
	}

	@Test
	@DisplayName("분산락 적용: 같은 좌석에 동시에 10명이 예약 요청 시 1명만 성공해야 함")
	@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
	void testDistributedLockForReservation() throws InterruptedException {
		// given
		int threadCount = 10;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch endLatch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		List<Reservation> successfulReservations = new ArrayList<>();
		List<Exception> exceptions = new ArrayList<>();

		// when: 여러 스레드가 동시에 같은 좌석 예약 시도
		for (int i = 0; i < threadCount; i++) {
			final int currentUserId = (int) (userId + i);
			executorService.submit(() -> {
				try {
					endLatch.countDown();
					startLatch.await();

					// 분산락이 적용된 예약 요청
					Reservation reservation = reserveConcertUseCase.execute(
							(long) currentUserId,
							seatId,
							null
					);

					if (reservation != null && reservation.getStatus() == ReservationStatus.HOLD) {
						successCount.incrementAndGet();
						successfulReservations.add(reservation);
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
					exceptions.add(e);
					// 예외 정보 출력 (디버깅용)
					System.err.println("Thread " + Thread.currentThread().getId() + " 예외: " + 
						e.getClass().getSimpleName() + " - " + e.getMessage());
					if (e.getCause() != null) {
						System.err.println("  원인: " + e.getCause().getClass().getSimpleName() + " - " + e.getCause().getMessage());
					}
				}
			});
		}

		// 모든 스레드가 준비될 때까지 대기
		endLatch.await();
		startLatch.countDown();

		executorService.shutdown();
		while (!executorService.isTerminated()) {
			Thread.sleep(100);
		}

		// then: 분산락이 적용되어 1명만 성공해야 함
		assertThat(successCount.get())
				.as("분산락 적용 시 동시 예약 요청에서 1명만 성공해야 함")
				.isEqualTo(1);

		assertThat(failureCount.get())
				.as("나머지는 실패해야 함")
				.isEqualTo(threadCount - 1);

		// 실제 DB에 저장된 HOLD 상태 예약도 1개여야 함
		long holdReservationCount = reservationJpaRepository.findAll().stream()
				.filter(r -> r.getStatus() == ReservationStatus.HOLD)
				.count();
		assertThat(holdReservationCount)
				.as("DB에 저장된 HOLD 상태 예약은 1개여야 함")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("분산락 적용: 같은 예약에 동시에 결제 요청 시 1건만 성공해야 함")
	@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
	void testDistributedLockForPayment() throws InterruptedException {
		// given: 예약 생성 (트랜잭션 내에서)
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		Long reservationId = transactionTemplate.execute(status -> {
			Reservation reservation = new Reservation();
			reservation.setUserId(userId);
			reservation.setSeat(seatRepository.findById(seatId).orElseThrow());
			reservation.setConcertSchedule(concertScheduleRepository.findById(concertScheduleId).orElseThrow());
			reservation.setStatus(ReservationStatus.HOLD);
			reservation.setHoldExpiresAt(LocalDateTime.now().plusMinutes(10));
			reservation.setAmountCents(new BigDecimal(80000));
			reservation = reservationJpaRepository.save(reservation);
			
			entityManager.flush();
			entityManager.clear();
			return reservation.getId();
		});

		// given
		int threadCount = 10;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch endLatch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		List<Payment> successfulPayments = new ArrayList<>();
		List<Exception> exceptions = new ArrayList<>();

		// when: 여러 스레드가 동시에 같은 예약에 대해 결제 시도
		for (int i = 0; i < threadCount; i++) {
			final int threadIndex = i;
			executorService.submit(() -> {
				try {
					endLatch.countDown();
					startLatch.await();

					// 분산락이 적용된 결제 요청
					Payment payment = processPaymentUseCase.execute(
							reservationId,
							"idempotency-" + Thread.currentThread().getId() + "-" + threadIndex
					);

					if (payment != null && payment.isApproved()) {
						successCount.incrementAndGet();
						successfulPayments.add(payment);
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
					exceptions.add(e);
				}
			});
		}

		// 모든 스레드가 준비될 때까지 대기
		endLatch.await();
		startLatch.countDown();

		executorService.shutdown();
		while (!executorService.isTerminated()) {
			Thread.sleep(100);
		}

		// then: 분산락이 적용되어 1건만 성공해야 함
		assertThat(successCount.get())
				.as("분산락 적용 시 동시 결제 요청에서 1건만 성공해야 함")
				.isEqualTo(1);

		assertThat(failureCount.get())
				.as("나머지는 실패해야 함")
				.isEqualTo(threadCount - 1);

		// 실제 DB에 저장된 승인된 결제도 1건이어야 함
		long approvedPaymentCount = paymentJpaRepository.findAll().stream()
				.filter(Payment::isApproved)
				.count();
		assertThat(approvedPaymentCount)
				.as("DB에 저장된 승인된 결제는 1건이어야 함")
				.isEqualTo(1);

		// 예약 상태가 PAID로 변경되었는지 확인
		Reservation updatedReservation = reservationJpaRepository.findById(reservationId).orElseThrow();
		assertThat(updatedReservation.getStatus())
				.as("예약 상태가 PAID로 변경되어야 함")
				.isEqualTo(ReservationStatus.PAID);
	}

	@Test
	@DisplayName("분산락 적용: 다른 좌석에 동시 예약 요청 시 모두 성공해야 함")
	@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
	void testDistributedLockForDifferentSeats() throws InterruptedException {
		// given: 여러 개의 좌석 생성 (트랜잭션 내에서)
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		int seatCount = 5;
		List<Long> seatIds = transactionTemplate.execute(status -> {
			List<Long> ids = new ArrayList<>();
			for (int i = 0; i < seatCount; i++) {
				Seat seat = new Seat();
				seat.setSeatNumber(i + 1);
				seat.setSeatGrade(SeatGrade.VIP);
				seat.setSeatStatus(SeatStatus.NON_RESERVATION);
				seat.setConcertSchedule(concertScheduleRepository.findById(concertScheduleId).orElseThrow());
				seat = seatRepository.save(seat);
				ids.add(seat.getSeatId());
			}
			entityManager.flush();
			entityManager.clear();
			return ids;
		});

		int threadCount = seatCount;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch endLatch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);

		// when: 각 스레드가 다른 좌석을 예약
		for (int i = 0; i < threadCount; i++) {
			final int currentUserId = (int) (userId + i);
			final Long targetSeatId = seatIds.get(i);

			executorService.submit(() -> {
				try {
					endLatch.countDown();
					startLatch.await();

					Reservation reservation = reserveConcertUseCase.execute(
							(long) currentUserId,
							targetSeatId,
							null
					);

					if (reservation != null && reservation.getStatus() == ReservationStatus.HOLD) {
						successCount.incrementAndGet();
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
				}
			});
		}

		endLatch.await();
		startLatch.countDown();

		executorService.shutdown();
		while (!executorService.isTerminated()) {
			Thread.sleep(100);
		}

		// then: 모두 성공해야 함 (다른 좌석이므로 분산락이 서로 다름)
		assertThat(successCount.get())
				.as("다른 좌석에 대한 예약은 모두 성공해야 함")
				.isEqualTo(seatCount);

		assertThat(failureCount.get())
				.as("실패한 예약이 없어야 함")
				.isEqualTo(0);
	}
}
