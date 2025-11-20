package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.common.domain.CommonEntity;
import kr.hhplus.be.server.concert.common.SeatGrade;
import kr.hhplus.be.server.concert.common.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@AllArgsConstructor
@RequiredArgsConstructor
public class Seat extends CommonEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long seatId;

	@Column(name = "seat_number")
	private int seatNumber;

	@Column(name = "seat_grade")
	@Enumerated(EnumType.ORDINAL)
	private SeatGrade seatGrade;

	@Column(name = "seat_status")
	@Enumerated(EnumType.ORDINAL)
	private SeatStatus seatStatus;

	@ManyToOne
	@JoinColumn(name = "concert_schedule_id")
	private ConcertSchedule concertSchedule;

}
