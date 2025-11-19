package kr.hhplus.be.server.point.domain;

import jakarta.persistence.*;

@Entity
public class Wallet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "wallet_id")
	private Long id;



}
