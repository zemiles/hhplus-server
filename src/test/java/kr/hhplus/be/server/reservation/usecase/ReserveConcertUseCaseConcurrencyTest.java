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
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.repository.ReservationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좌석 예약 동시성 테스트
 * 
 * 테스트 목적:
 * - SELECT FOR UPDATE를 사용한 좌석 예약 동시성 제어 검증
 * - 같은 좌석에 대해 여러 스레드가 동시에 예약 요청 시 1명만 성공해야 함
 * 
 * 테스트 시나리오:
 * 1. 같은 좌석에 대해 10명이 동시에 예약 요청
 * 2. SELECT FOR UPDATE가 제대로 작동하면 1명만 성공해야 함
 * 3. 나머지 9명은 예외 발생 또는 실패해야 함
 */
@SpringBootTest
@ActiveProfiles("h2")
class ReserveConcertUseCaseConcurrencyTest {

    @Autowired
    private ReserveConcertUseCase reserveConcertUseCase;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private ConcertScheduleRepository concertScheduleRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationJpaRepository reservationJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private Long seatId;
    private Long concertScheduleId;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 준비
        Concert concert = new Concert();
        concert.setConcertName("테스트 콘서트");
        concert.setConcertDec("동시성 테스트용 콘서트");
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

        // 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("같은 좌석에 동시에 10명이 예약 요청 시 1명만 성공해야 함")
    void testConcurrentReservation() throws InterruptedException {
        // given
        int threadCount = 10;  // 동시 요청 수
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);  // 시작 신호
        CountDownLatch endLatch = new CountDownLatch(threadCount);  // 완료 대기
        
        // 성공한 예약 수를 카운트
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Reservation> successfulReservations = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();

        // when: 여러 스레드가 동시에 같은 좌석 예약 시도
        for (int i = 0; i < threadCount; i++) {
            final int userId = i + 1;
            executorService.submit(() -> {
                try {
                    // 모든 스레드가 준비될 때까지 대기
                    endLatch.countDown();
                    startLatch.await();
                    
                    // 동시에 예약 요청
                    Reservation reservation = reserveConcertUseCase.execute(
                            (long) userId, 
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
                }
            });
        }

        // 모든 스레드가 준비될 때까지 대기
        endLatch.await();
        
        // 동시에 시작하도록 신호
        startLatch.countDown();

        // 모든 작업이 완료될 때까지 대기
        executorService.shutdown();
        while (!executorService.isTerminated()) {
            Thread.sleep(100);
        }

        // then: 1명만 성공해야 함
        assertThat(successCount.get())
                .as("동시 예약 요청 시 1명만 성공해야 함")
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
        
        // 성공한 예약의 좌석 ID가 올바른지 확인
        if (!successfulReservations.isEmpty()) {
            assertThat(successfulReservations.get(0).getSeat().getSeatId())
                    .isEqualTo(seatId);
        }
    }

    @Test
    @DisplayName("다른 좌석에 동시 예약 요청 시 모두 성공해야 함")
    void testConcurrentReservationDifferentSeats() throws InterruptedException {
        // given: 여러 개의 좌석 생성
        int seatCount = 5;
        List<Long> seatIds = new ArrayList<>();
        
        for (int i = 0; i < seatCount; i++) {
            Seat seat = new Seat();
            seat.setSeatNumber(i + 1);
            seat.setSeatGrade(SeatGrade.VIP);
            seat.setSeatStatus(SeatStatus.NON_RESERVATION);
            seat.setConcertSchedule(concertScheduleRepository.findById(concertScheduleId).orElseThrow());
            seat = seatRepository.save(seat);
            seatIds.add(seat.getSeatId());
        }
        
        entityManager.flush();
        entityManager.clear();

        int threadCount = seatCount;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // when: 각 스레드가 다른 좌석을 예약
        for (int i = 0; i < threadCount; i++) {
            final int userId = i + 1;
            final Long targetSeatId = seatIds.get(i);
            
            executorService.submit(() -> {
                try {
                    endLatch.countDown();
                    startLatch.await();
                    
                    Reservation reservation = reserveConcertUseCase.execute(
                            (long) userId, 
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

        // then: 모두 성공해야 함
        assertThat(successCount.get())
                .as("다른 좌석에 대한 예약은 모두 성공해야 함")
                .isEqualTo(seatCount);
        
        assertThat(failureCount.get())
                .as("실패한 예약이 없어야 함")
                .isEqualTo(0);
    }
}
