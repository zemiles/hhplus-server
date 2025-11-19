package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.concert.common.SeatGrade;
import kr.hhplus.be.server.concert.common.SeatStatus;

@Entity
public class Seat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long seatId;

	@Column(name = "seat_number")
	private int seatNumber;

	@Column(name = "seat_grade")
	private SeatGrade seatGrade;

	@Column(name = "seat_status")
	private SeatStatus seatStatus;

	@ManyToOne
	@JoinColumn(name = "concert_schedule_id")
	private ConcertSchedule concertSchedule;

}
