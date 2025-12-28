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
import kr.hhplus.be.server.point.domain.Ledger;
import kr.hhplus.be.server.point.domain.LedgerType;
import kr.hhplus.be.server.point.domain.User;
import kr.hhplus.be.server.point.domain.Wallet;
import kr.hhplus.be.server.point.common.UserStatus;
import kr.hhplus.be.server.point.repository.LedgerRepository;
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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
 * 잔액 차감 동시성 테스트
 * 
 * 테스트 목적:
 * - 조건부 UPDATE를 사용한 잔액 차감 동시성 제어 검증
 * - 잔액이 부족할 때 여러 스레드가 동시에 차감 시도 시 1명만 성공해야 함
 * - 최종 잔액이 음수가 되지 않아야 함
 * 
 * 테스트 시나리오:
 * 1. 잔액이 100원인 지갑에 대해
 * 2. 여러 스레드가 동시에 100원씩 차감 시도
 * 3. 조건부 UPDATE가 제대로 작동하면 1명만 성공하고 나머지는 실패해야 함
 * 4. 최종 잔액이 음수가 되지 않아야 함
 */
@SpringBootTest
@ActiveProfiles("h2")
class ProcessPaymentUseCaseConcurrencyTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private ReserveConcertUseCase reserveConcertUseCase;

    @Autowired
    private WalletRepository walletRepository;

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
    private LedgerRepository ledgerRepository;

    @Autowired
    private EntityManager entityManager;

    private Long userId;
    private Long walletId;
    private Long reservationId;
    private BigDecimal initialBalance;
    private BigDecimal paymentAmount;

    @BeforeEach
    void setUp() {
        // 사용자 생성
        User user = new User();
        user.setUserName("테스트 사용자");
        user.setUserEmail("test@test.com");
        user.setUserTel("01012345678");
        user.setUserStatus(UserStatus.NORMAL);
        
        // User는 JPA로 저장 (UserRepository가 없으므로 EntityManager 사용)
        entityManager.persist(user);
        entityManager.flush();
        userId = user.getId();

        // 지갑 생성 (잔액: 10000원 = 100원)
        initialBalance = new BigDecimal(10000);
        paymentAmount = new BigDecimal(10000); // 100원
        
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setBalanceCents(initialBalance);
        wallet.setCurrency("KRW");
        wallet = walletRepository.save(wallet);
        walletId = wallet.getId();

        // 콘서트 및 예약 생성
        Concert concert = new Concert();
        concert.setConcertName("테스트 콘서트");
        concert.setConcertDec("동시성 테스트용 콘서트");
        concert.setConcertStatus(ConcertStatus.RESERVATION);
        concert = concertRepository.save(concert);

        ConcertSchedule schedule = new ConcertSchedule();
        schedule.setConcert(concert);
        schedule.setConcertDate("20241225");
        schedule.setConcertTime("180000");
        schedule.setConcertPrice(new BigDecimal(100)); // 100원
        schedule = concertScheduleRepository.save(schedule);

        Seat seat = new Seat();
        seat.setSeatNumber(1);
        seat.setSeatGrade(SeatGrade.VIP);
        seat.setSeatStatus(SeatStatus.NON_RESERVATION);
        seat.setConcertSchedule(schedule);
        seat = seatRepository.save(seat);

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setSeat(seat);
        reservation.setConcertSchedule(schedule);
        reservation.setStatus(ReservationStatus.HOLD);
        reservation.setHoldExpiresAt(LocalDateTime.now().plusMinutes(10));
        reservation.setAmountCents(paymentAmount);
        reservation = reservationJpaRepository.save(reservation);
        reservationId = reservation.getId();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("잔액이 100원일 때 동시에 10명이 100원씩 차감 시도 시 1명만 성공해야 함")
    void testConcurrentBalanceDeduction() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();

        // when: 여러 스레드가 동시에 결제 시도
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    endLatch.countDown();
                    startLatch.await();
                    
                    // 각 스레드마다 새로운 예약 생성 (같은 좌석, 같은 사용자)
                    // 실제로는 각각 다른 예약이어야 하지만, 동시성 테스트를 위해
                    // 같은 예약에 대해 여러 번 결제 시도하는 시나리오는 비즈니스 로직상 불가능
                    // 따라서 각 스레드마다 새로운 예약을 생성해야 함
                    
                    // 새로운 예약 생성
                    Reservation newReservation = createNewReservation();
                    
                    // 동시에 결제 요청
                    Payment payment = processPaymentUseCase.execute(
                            newReservation.getId(), 
                            "idempotency-" + Thread.currentThread().getId() + "-" + threadIndex
                    );
                    
                    if (payment != null && payment.isApproved()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                }
            });
        }

        endLatch.await();
        startLatch.countDown();

        executorService.shutdown();
        while (!executorService.isTerminated()) {
            Thread.sleep(100);
        }

        // then: 1명만 성공해야 함
        assertThat(successCount.get())
                .as("잔액이 부족한 상황에서 동시 결제 시도 시 1명만 성공해야 함")
                .isEqualTo(1);
        
        // 최종 잔액이 음수가 되지 않아야 함
        BigDecimal finalBalance = walletRepository.getBalance(walletId);
        assertThat(finalBalance.compareTo(BigDecimal.ZERO))
                .as("최종 잔액이 음수가 되지 않아야 함")
                .isGreaterThanOrEqualTo(0);
        
        // 실제 결제 성공 건수도 1개여야 함
        long approvedPaymentCount = paymentJpaRepository.findAll().stream()
                .filter(Payment::isApproved)
                .count();
        assertThat(approvedPaymentCount)
                .as("승인된 결제는 1건이어야 함")
                .isEqualTo(1);
        
        // 거래 이력도 1개여야 함
        long ledgerCount = ledgerRepository.findAll().stream()
                .filter(l -> l.getType() == LedgerType.PAYMENT)
                .count();
        assertThat(ledgerCount)
                .as("결제 거래 이력은 1건이어야 함")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("잔액이 충분할 때 동시 결제 시도 시 모두 성공해야 함")
    void testConcurrentBalanceDeductionSufficientBalance() throws InterruptedException {
        // given: 잔액을 충분하게 설정 (1000원)
        BigDecimal sufficientBalance = new BigDecimal(100000); // 1000원
        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        wallet.setBalanceCents(sufficientBalance);
        walletRepository.save(wallet);
        
        entityManager.flush();
        entityManager.clear();

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // when: 여러 스레드가 동시에 결제 시도
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    endLatch.countDown();
                    startLatch.await();
                    
                    Reservation newReservation = createNewReservation();
                    
                    Payment payment = processPaymentUseCase.execute(
                            newReservation.getId(), 
                            "idempotency-" + Thread.currentThread().getId() + "-" + threadIndex
                    );
                    
                    if (payment != null && payment.isApproved()) {
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

        // then: 모두 성공해야 함
        assertThat(successCount.get())
                .as("잔액이 충분할 때는 모두 성공해야 함")
                .isEqualTo(threadCount);
        
        assertThat(failureCount.get())
                .as("실패한 결제가 없어야 함")
                .isEqualTo(0);
    }

    /**
     * 새로운 예약 생성 헬퍼 메서드
     */
    private Reservation createNewReservation() {
        ConcertSchedule schedule = concertScheduleRepository.findAll().get(0);
        
        Seat seat = new Seat();
        seat.setSeatNumber((int) (Math.random() * 1000) + 100); // 랜덤 좌석 번호
        seat.setSeatGrade(SeatGrade.VIP);
        seat.setSeatStatus(SeatStatus.NON_RESERVATION);
        seat.setConcertSchedule(schedule);
        seat = seatRepository.save(seat);

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setSeat(seat);
        reservation.setConcertSchedule(schedule);
        reservation.setStatus(ReservationStatus.HOLD);
        reservation.setHoldExpiresAt(LocalDateTime.now().plusMinutes(10));
        reservation.setAmountCents(paymentAmount);
        reservation = reservationJpaRepository.save(reservation);
        
        entityManager.flush();
        return reservation;
    }
}
