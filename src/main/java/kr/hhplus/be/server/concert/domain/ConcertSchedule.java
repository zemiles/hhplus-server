package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;

import java.math.BigDecimal;
import java.util.Date;

@Entity
public class ConcertSchedule extends CommonEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long concertScheduleId;

	@Column
	private String concertDate;

	@Column
	private String concertTime;

	@Column
	private BigDecimal concertPrice;
	
	/*
	* 콘서트 ID도 같이 넣기
	* */
	
}
