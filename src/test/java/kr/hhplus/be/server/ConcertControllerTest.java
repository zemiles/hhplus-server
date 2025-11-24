package kr.hhplus.be.server;


import kr.hhplus.be.server.concert.common.ConcertStatus;
import kr.hhplus.be.server.concert.common.SeatGrade;
import kr.hhplus.be.server.concert.common.SeatStatus;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.Seat;
import kr.hhplus.be.server.concert.dto.ConcertResponse;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
import kr.hhplus.be.server.concert.service.ConcertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcertControllerTest {

	@Mock
	private ConcertService concertService;

	@Mock
	private ConcertRepository concertRepository;

	@Test
	void getConcert(){
		long concertId = 1L;
		Concert concert = new Concert(1L, "아이유 콘서트", "아이유 크리스마스 콘서트", ConcertStatus.RESERVATION);
		ConcertSchedule concertSchedule = new ConcertSchedule(1L, "20251225", "180000", new BigDecimal(80000), concert);
		Seat seat = new Seat(1L, 1, SeatGrade.VIP, SeatStatus.NON_RESERVATION, concertSchedule);



		when(concertRepository.findById(1L)).thenReturn(Optional.of(concert));

		ConcertResponse concertResponse = concertService.getConcerts(concertId);


		assertThat(concertResponse).isNotNull();
		assertThat(concertResponse.getConcertId()).isEqualTo(1L);
		assertThat(concertResponse.getConcertName()).isEqualTo("아이유 콘서트");

	}

}
