package kr.hhplus.be.server.reservation.domain;


import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments_main")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends CommonEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "payment_id")
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "reservation_id")
	private Long reservationId;

	@Column(name = "total_amount_cents", nullable = false)
	private BigDecimal totalAmountCents;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.ORDINAL)
	private PaymentStatus status;

	@Column(name = "idempotency_key", unique = true, nullable = false)
	private String idempotencyKey;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	public void markAsApproved() {
		this.status = PaymentStatus.APPROVED;
		this.approvedAt = LocalDateTime.now();
	}

	public boolean isApproved() {
		return status == PaymentStatus.APPROVED;
	}

}
