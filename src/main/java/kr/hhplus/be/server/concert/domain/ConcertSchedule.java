package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class ConcertSchedule extends CommonEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "concert_scheduled_id")
	private Long concertScheduleId;

	@Column(name = "concert_date")
	private String concertDate;

	@Column(name = "concert_time")
	private String concertTime;

	@Column(name = "concert_price")
	private BigDecimal concertPrice;

	@ManyToOne
	@JoinColumn(name = "concert_id")
	private Concert concert;

	public ConcertSchedule() {}

	public ConcertSchedule(Long concertScheduleId, String concertDate, String concertTime, BigDecimal concertPrice, Concert concert) {
		this.concertScheduleId = concertScheduleId;
		this.concertDate = concertDate;
		this.concertTime = concertTime;
		this.concertPrice = concertPrice;
		this.concert = concert;
	}

}
