package kr.hhplus.be.server.concert.repository;

import kr.hhplus.be.server.concert.dto.ConcertResponse;

import java.util.List;

public interface ConcertCustomRepository {

	List<ConcertResponse> findConcertDate(Long concertId);

}
