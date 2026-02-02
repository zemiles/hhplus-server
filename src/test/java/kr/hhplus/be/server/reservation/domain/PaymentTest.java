package kr.hhplus.be.server.reservation.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment 도메인 단위 테스트
 * 
 * Payment 엔티티의 비즈니스 로직을 검증합니다.
 * - markAsApproved(): 결제 승인 처리
 * - isApproved(): 결제 승인 여부 확인
 */
class PaymentTest {

	private Payment payment;

	@BeforeEach
	void setUp() {
		payment = new Payment();
		payment.setUserId(1L);
		payment.setReservationId(1L);
		payment.setTotalAmountCents(new BigDecimal(80000));
		payment.setStatus(PaymentStatus.INIT);
		payment.setIdempotencyKey("test-key");
	}

	@Test
	@DisplayName("markAsApproved 호출 시 상태가 APPROVED로 변경되고 승인 시간이 설정됨")
	void testMarkAsApproved_ChangesStatusToApprovedAndSetsApprovedAt() {
		// given
		payment.setStatus(PaymentStatus.INIT);
		LocalDateTime beforeApproval = LocalDateTime.now();
		
		// 약간의 시간 차이를 두기 위해 잠시 대기
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		// when
		payment.markAsApproved();

		// then
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		assertThat(payment.getApprovedAt()).isNotNull();
		assertThat(payment.getApprovedAt()).isAfterOrEqualTo(beforeApproval);
		assertThat(payment.isApproved()).isTrue();
	}

	@Test
	@DisplayName("APPROVED 상태일 때 isApproved는 true 반환")
	void testIsApproved_ApprovedStatus_ReturnsTrue() {
		// given
		payment.setStatus(PaymentStatus.APPROVED);

		// when
		boolean approved = payment.isApproved();

		// then
		assertThat(approved).isTrue();
	}

	@Test
	@DisplayName("INIT 상태일 때 isApproved는 false 반환")
	void testIsApproved_InitStatus_ReturnsFalse() {
		// given
		payment.setStatus(PaymentStatus.INIT);

		// when
		boolean approved = payment.isApproved();

		// then
		assertThat(approved).isFalse();
	}

	@Test
	@DisplayName("FAILED 상태일 때 isApproved는 false 반환")
	void testIsApproved_FailedStatus_ReturnsFalse() {
		// given
		payment.setStatus(PaymentStatus.FAILED);

		// when
		boolean approved = payment.isApproved();

		// then
		assertThat(approved).isFalse();
	}
}
