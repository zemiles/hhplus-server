package kr.hhplus.be.server.reservation.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reservation 도메인 단위 테스트
 * 
 * Reservation 엔티티의 비즈니스 로직을 검증합니다.
 * - isExpired(): 예약 만료 여부 확인
 * - canBePaid(): 결제 가능 여부 확인
 * - markAsPaid(): 결제 완료 처리
 * - markAsExpired(): 만료 처리
 */
class ReservationTest {

	private Reservation reservation;
	private LocalDateTime futureTime;
	private LocalDateTime pastTime;

	@BeforeEach
	void setUp() {
		reservation = new Reservation();
		futureTime = LocalDateTime.now().plusMinutes(15);
		pastTime = LocalDateTime.now().minusMinutes(5);
	}

	@Test
	@DisplayName("만료 시간이 지나지 않았으면 만료되지 않음")
	void testIsExpired_FutureTime_ReturnsFalse() {
		// given
		reservation.setHoldExpiresAt(futureTime);

		// when
		boolean expired = reservation.isExpired();

		// then
		assertThat(expired).isFalse();
	}

	@Test
	@DisplayName("만료 시간이 지났으면 만료됨")
	void testIsExpired_PastTime_ReturnsTrue() {
		// given
		reservation.setHoldExpiresAt(pastTime);

		// when
		boolean expired = reservation.isExpired();

		// then
		assertThat(expired).isTrue();
	}

	@Test
	@DisplayName("만료 시간이 null이면 만료되지 않음")
	void testIsExpired_NullTime_ReturnsFalse() {
		// given
		reservation.setHoldExpiresAt(null);

		// when
		boolean expired = reservation.isExpired();

		// then
		assertThat(expired).isFalse();
	}

	@Test
	@DisplayName("HOLD 상태이고 만료되지 않았으면 결제 가능")
	void testCanBePaid_HoldAndNotExpired_ReturnsTrue() {
		// given
		reservation.setStatus(ReservationStatus.HOLD);
		reservation.setHoldExpiresAt(futureTime);

		// when
		boolean canBePaid = reservation.canBePaid();

		// then
		assertThat(canBePaid).isTrue();
	}

	@Test
	@DisplayName("HOLD 상태이지만 만료되었으면 결제 불가능")
	void testCanBePaid_HoldButExpired_ReturnsFalse() {
		// given
		reservation.setStatus(ReservationStatus.HOLD);
		reservation.setHoldExpiresAt(pastTime);

		// when
		boolean canBePaid = reservation.canBePaid();

		// then
		assertThat(canBePaid).isFalse();
	}

	@Test
	@DisplayName("PAID 상태이면 결제 불가능")
	void testCanBePaid_PaidStatus_ReturnsFalse() {
		// given
		reservation.setStatus(ReservationStatus.PAID);
		reservation.setHoldExpiresAt(futureTime);

		// when
		boolean canBePaid = reservation.canBePaid();

		// then
		assertThat(canBePaid).isFalse();
	}

	@Test
	@DisplayName("EXPIRED 상태이면 결제 불가능")
	void testCanBePaid_ExpiredStatus_ReturnsFalse() {
		// given
		reservation.setStatus(ReservationStatus.EXPIRED);
		reservation.setHoldExpiresAt(futureTime);

		// when
		boolean canBePaid = reservation.canBePaid();

		// then
		assertThat(canBePaid).isFalse();
	}

	@Test
	@DisplayName("markAsPaid 호출 시 상태가 PAID로 변경됨")
	void testMarkAsPaid_ChangesStatusToPaid() {
		// given
		reservation.setStatus(ReservationStatus.HOLD);

		// when
		reservation.markAsPaid();

		// then
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAID);
	}

	@Test
	@DisplayName("markAsExpired 호출 시 상태가 EXPIRED로 변경됨")
	void testMarkAsExpired_ChangesStatusToExpired() {
		// given
		reservation.setStatus(ReservationStatus.HOLD);

		// when
		reservation.markAsExpired();

		// then
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
	}
}
