package kr.hhplus.be.server.reservation.service;

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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예약 만료 스케줄러 테스트
 * 
 * 테스트 목적:
 * - 만료된 HOLD 상태 예약이 자동으로 EXPIRED 상태로 변경되는지 검증
 * - 스케줄러가 정상적으로 작동하는지 확인
 * 
 * 테스트 시나리오:
 * 1. 만료된 예약과 아직 유효한 예약을 생성
 * 2. 스케줄러 실행
 * 3. 만료된 예약만 EXPIRED 상태로 변경되는지 확인
 */
@SpringBootTest
@ActiveProfiles("h2")
class ReservationExpirationSchedulerTest {

    @Autowired
    private ReservationExpirationScheduler reservationExpirationScheduler;

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

    private Long concertScheduleId;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 준비
        Concert concert = new Concert();
        concert.setConcertName("테스트 콘서트");
        concert.setConcertDec("스케줄러 테스트용 콘서트");
        concert.setConcertStatus(ConcertStatus.RESERVATION);
        concert = concertRepository.save(concert);

        ConcertSchedule schedule = new ConcertSchedule();
        schedule.setConcert(concert);
        schedule.setConcertDate("20241225");
        schedule.setConcertTime("180000");
        schedule.setConcertPrice(new BigDecimal(80000));
        schedule = concertScheduleRepository.save(schedule);
        concertScheduleId = schedule.getConcertScheduleId();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("만료된 예약이 자동으로 EXPIRED 상태로 변경되어야 함")
    @Transactional
    void testExpireReservations() {
        // given: 만료된 예약과 유효한 예약 생성
        ConcertSchedule schedule = concertScheduleRepository.findById(concertScheduleId).orElseThrow();
        
        // 만료된 예약 (10분 전에 만료)
        Seat expiredSeat = createSeat(schedule, 1);
        Reservation expiredReservation = createReservation(expiredSeat, 1L, 
                LocalDateTime.now().minusMinutes(11)); // 11분 전 = 만료됨
        
        // 유효한 예약 (아직 만료되지 않음)
        Seat validSeat = createSeat(schedule, 2);
        Reservation validReservation = createReservation(validSeat, 2L, 
                LocalDateTime.now().plusMinutes(5)); // 5분 후 = 아직 유효
        
        // 또 다른 만료된 예약
        Seat expiredSeat2 = createSeat(schedule, 3);
        Reservation expiredReservation2 = createReservation(expiredSeat2, 3L, 
                LocalDateTime.now().minusMinutes(15)); // 15분 전 = 만료됨

        entityManager.flush();
        entityManager.clear();

        // 초기 상태 확인
        List<Reservation> allReservations = reservationJpaRepository.findAll();
        long holdCountBefore = allReservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.HOLD)
                .count();
        assertThat(holdCountBefore).isEqualTo(3); // 모두 HOLD 상태

        // when: 스케줄러 실행
        reservationExpirationScheduler.expireReservations();

        entityManager.flush();
        entityManager.clear();

        // then: 만료된 예약만 EXPIRED 상태로 변경되어야 함
        List<Reservation> reservationsAfter = reservationJpaRepository.findAll();
        
        Reservation expired1 = reservationsAfter.stream()
                .filter(r -> r.getId().equals(expiredReservation.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(expired1.getStatus())
                .as("만료된 예약 1은 EXPIRED 상태여야 함")
                .isEqualTo(ReservationStatus.EXPIRED);
        
        Reservation expired2 = reservationsAfter.stream()
                .filter(r -> r.getId().equals(expiredReservation2.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(expired2.getStatus())
                .as("만료된 예약 2는 EXPIRED 상태여야 함")
                .isEqualTo(ReservationStatus.EXPIRED);
        
        Reservation valid = reservationsAfter.stream()
                .filter(r -> r.getId().equals(validReservation.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(valid.getStatus())
                .as("유효한 예약은 HOLD 상태를 유지해야 함")
                .isEqualTo(ReservationStatus.HOLD);
        
        // 전체 통계 확인
        long expiredCount = reservationsAfter.stream()
                .filter(r -> r.getStatus() == ReservationStatus.EXPIRED)
                .count();
        assertThat(expiredCount)
                .as("만료된 예약은 2개여야 함")
                .isEqualTo(2);
        
        long holdCountAfter = reservationsAfter.stream()
                .filter(r -> r.getStatus() == ReservationStatus.HOLD)
                .count();
        assertThat(holdCountAfter)
                .as("유효한 HOLD 예약은 1개여야 함")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("만료된 예약이 없으면 아무것도 변경되지 않아야 함")
    @Transactional
    void testExpireReservationsNoExpired() {
        // given: 만료되지 않은 예약만 생성
        ConcertSchedule schedule = concertScheduleRepository.findById(concertScheduleId).orElseThrow();
        
        Seat seat1 = createSeat(schedule, 1);
        Reservation reservation1 = createReservation(seat1, 1L, 
                LocalDateTime.now().plusMinutes(5));
        
        Seat seat2 = createSeat(schedule, 2);
        Reservation reservation2 = createReservation(seat2, 2L, 
                LocalDateTime.now().plusMinutes(10));

        entityManager.flush();
        entityManager.clear();

        // when: 스케줄러 실행
        reservationExpirationScheduler.expireReservations();

        entityManager.flush();
        entityManager.clear();

        // then: 모든 예약이 HOLD 상태를 유지해야 함
        List<Reservation> reservations = reservationJpaRepository.findAll();
        long holdCount = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.HOLD)
                .count();
        assertThat(holdCount)
                .as("만료된 예약이 없으면 모든 예약이 HOLD 상태를 유지해야 함")
                .isEqualTo(2);
        
        long expiredCount = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.EXPIRED)
                .count();
        assertThat(expiredCount)
                .as("만료된 예약이 없어야 함")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("이미 EXPIRED 상태인 예약은 변경되지 않아야 함")
    @Transactional
    void testExpireReservationsAlreadyExpired() {
        // given: 이미 EXPIRED 상태인 예약 생성
        ConcertSchedule schedule = concertScheduleRepository.findById(concertScheduleId).orElseThrow();
        
        Seat seat = createSeat(schedule, 1);
        Reservation reservation = createReservation(seat, 1L, 
                LocalDateTime.now().minusMinutes(11));
        reservation.setStatus(ReservationStatus.EXPIRED); // 이미 EXPIRED 상태
        reservationJpaRepository.save(reservation);

        entityManager.flush();
        entityManager.clear();

        // when: 스케줄러 실행
        reservationExpirationScheduler.expireReservations();

        entityManager.flush();
        entityManager.clear();

        // then: 상태가 변경되지 않아야 함
        Reservation found = reservationJpaRepository.findById(reservation.getId()).orElseThrow();
        assertThat(found.getStatus())
                .as("이미 EXPIRED 상태인 예약은 변경되지 않아야 함")
                .isEqualTo(ReservationStatus.EXPIRED);
    }

    /**
     * 좌석 생성 헬퍼 메서드
     */
    private Seat createSeat(ConcertSchedule schedule, int seatNumber) {
        Seat seat = new Seat();
        seat.setSeatNumber(seatNumber);
        seat.setSeatGrade(SeatGrade.VIP);
        seat.setSeatStatus(SeatStatus.NON_RESERVATION);
        seat.setConcertSchedule(schedule);
        return seatRepository.save(seat);
    }

    /**
     * 예약 생성 헬퍼 메서드
     */
    private Reservation createReservation(Seat seat, Long userId, LocalDateTime holdExpiresAt) {
        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setSeat(seat);
        reservation.setConcertSchedule(seat.getConcertSchedule());
        reservation.setStatus(ReservationStatus.HOLD);
        reservation.setHoldExpiresAt(holdExpiresAt);
        reservation.setAmountCents(new BigDecimal(80000));
        return reservationJpaRepository.save(reservation);
    }
}
