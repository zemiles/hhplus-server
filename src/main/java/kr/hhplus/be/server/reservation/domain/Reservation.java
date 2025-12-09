package kr.hhplus.be.server.reservation.domain;


import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.Seat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
public class Reservation extends CommonEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reservation_id")
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concert_schedule_id", nullable = false)
	private ConcertSchedule concertSchedule;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "seat_id", nullable = false)
	private Seat seat;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.ORDINAL)
	private ReservationStatus status;

	@Column(name = "hold_expires_at")
	private LocalDateTime holdExpiresAt;

	@Column(name = "amount_cents", nullable = false)
	private BigDecimal amountCents;

	@Column(name = "idempotency_key")
	private String idempotencyKey;

	//비즈니스 로직 메서드
	public boolean isExpired() {
		return holdExpiresAt != null && LocalDateTime.now().isAfter(holdExpiresAt);
	}

	public boolean canBePaid() {
		return status == ReservationStatus.HOLD && !isExpired();
	}

	public void markAsPaid() {
		this.status = ReservationStatus.PAID;
	}

	public void markAsExpired() {
		this.status = ReservationStatus.EXPIRED;
	}


}
