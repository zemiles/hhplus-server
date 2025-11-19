package kr.hhplus.be.server.concert.service;

import kr.hhplus.be.server.concert.dto.ConcertListResponse;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ConcertService {

	private final ConcertRepository concertRepository;

	/*
	* todo
	* 예약가능한 상태를 조회하는거라서 ConcertId를 가지고 ConcertScheduleID를 조회해서 현재 예약 가능한 좌석이 있는 날짜를 리턴해주면 된다.
	* */
	public ConcertListResponse getConcerts(int concertId){
		return null;
	}

}
