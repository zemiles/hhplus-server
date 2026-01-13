package kr.hhplus.be.server.point.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;
import kr.hhplus.be.server.point.common.UserStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users") // H2에서 user는 예약어이므로 users로 변경
@Getter
@Setter
@NoArgsConstructor
public class User extends CommonEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long id;

	@Column(name = "user_email")
	private String userEmail;

	@Column(name = "user_tel")
	private String userTel;

	@Column(name = "user_name")
	private String userName;

	@Column(name = "user_status")
	private UserStatus userStatus;

	@OneToOne
	@JoinColumn(name = "wallet_id")
	private Wallet walletId;

}
