package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;

@Entity
public class Concert extends CommonEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private String concertNm;
	
	@Column
	private String concertDec;
	
	/** 
	 * todo
	 * 콘서트 상태 enum 처리
	 * */
	@Column
	private String status;
}
