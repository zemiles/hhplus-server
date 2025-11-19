package kr.hhplus.be.server.concert.controller;

import kr.hhplus.be.server.concert.dto.ConcertListResponse;
import kr.hhplus.be.server.concert.service.ConcertService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservation/possibility")
public class ConcertController {

	private final ConcertService concertService;

	public ConcertController(ConcertService concertService) {
		this.concertService = concertService;
	}

	@GetMapping("/{concert_id}/dates")
	public ConcertListResponse getConcerts(@PathVariable int concert_id) {
		return concertService.getConcerts(concert_id);
	}

}
