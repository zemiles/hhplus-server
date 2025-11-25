package kr.hhplus.be.server.point.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;

@Entity
public class Wallet extends CommonEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "wallet_id")
	private Long id;

	@OneToOne
	@JoinColumn(name = "user_id")
	private User user;

}
