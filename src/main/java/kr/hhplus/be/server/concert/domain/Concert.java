package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;
import kr.hhplus.be.server.concert.common.ConcertStatus;

@Entity
public class Concert extends CommonEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "concert_id")
	private Long id;

	@Column(name = "concert_name")
	private String concertName;
	
	@Column(name = "concert_dec")
	private String concertDec;
	
	/** 
	 * todo
	 * 콘서트 상태 enum 처리
	 * */
	@Column(name = "concert_status")
	private ConcertStatus concertStatus;
}
