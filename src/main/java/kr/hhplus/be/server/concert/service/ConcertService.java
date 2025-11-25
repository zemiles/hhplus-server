package kr.hhplus.be.server.concert.service;

import ch.qos.logback.classic.spi.IThrowableProxy;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.dto.ConcertResponse;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
import kr.hhplus.be.server.concert.repository.ConcertScheduleRepository;
import kr.hhplus.be.server.concert.repository.SeatRepository;
import kr.hhplus.be.server.concert.repository.impl.ConcertRepositoryImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ConcertService {

	private final ConcertRepository concertRepository;

	private final ConcertScheduleRepository concertScheduleRepository;

	private final SeatRepository seatRepository;

	private final ConcertRepositoryImpl concertRepositoryImpl;

	ConcertService(ConcertRepository concertRepository,
	               ConcertScheduleRepository concertScheduleRepository,
	               SeatRepository seatRepository,
	               ConcertRepositoryImpl concertRepositoryImpl) {
		this.concertRepository = concertRepository;
		this.concertScheduleRepository = concertScheduleRepository;
		this.seatRepository = seatRepository;
		this.concertRepositoryImpl = concertRepositoryImpl;
	}

	/*
	* todo
	* 예약가능한 상태를 조회하는거라서 ConcertId를 가지고 ConcertScheduleID를 조회해서 현재 예약 가능한 좌석이 있는 날짜를 리턴해주면 된다.
	* */
	public List<ConcertResponse> getConcerts(Long concertId){
		Concert concert = concertRepository.findById(concertId)
							.orElseThrow(() -> new RuntimeException("concert를 찾을수 없습니다."));

		return concertRepositoryImpl.findConcertDate(concert.getId());
	}

}
