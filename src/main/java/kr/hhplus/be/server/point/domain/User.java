package kr.hhplus.be.server.point.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;

@Entity
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

}
