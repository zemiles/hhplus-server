package kr.hhplus.be.server.point.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;

import java.math.BigDecimal;

@Entity
public class Ledger extends CommonEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ledger_id")
	private Long id;

	@Column(name = "amount")
	private BigDecimal amount;

	@Column(name = "charge_date")
	private String chargeDate;

	@Column(name = "charge_time")
	private String chargeTime;

	@ManyToOne
	@JoinColumn(name = "wallet_id")
	private Wallet wallet;


}
