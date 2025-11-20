package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;
import kr.hhplus.be.server.concert.common.ConcertStatus;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@AllArgsConstructor
@RequiredArgsConstructor
public class Concert extends CommonEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "concert_id")
	private Long id;

	@Column(name = "concert_name")
	private String concertName;
	
	@Column(name = "concert_dec")
	private String concertDec;

	@Column(name = "concert_status")
	@Enumerated(EnumType.ORDINAL)
	private ConcertStatus concertStatus;
}
