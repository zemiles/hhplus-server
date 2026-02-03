package kr.hhplus.be.server.concert.service;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.dto.ConcertResponse;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
import kr.hhplus.be.server.concert.repository.ConcertScheduleRepository;
import kr.hhplus.be.server.concert.repository.SeatRepository;
import kr.hhplus.be.server.concert.repository.impl.ConcertRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * ConcertService 단위 테스트
 * 
 * Mock을 사용하여 의존성을 격리하고 비즈니스 로직을 검증합니다.
 * - 콘서트 조회 성공
 * - 콘서트를 찾을 수 없는 경우
 */
@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

	@Mock
	private ConcertRepository concertRepository;

	@Mock
	private ConcertScheduleRepository concertScheduleRepository;

	@Mock
	private SeatRepository seatRepository;

	@Mock
	private ConcertRepositoryImpl concertRepositoryImpl;

	@InjectMocks
	private ConcertService concertService;

	private Long concertId;
	private Concert concert;

	@BeforeEach
	void setUp() {
		concertId = 1L;

		concert = new Concert();
		concert.setId(concertId);
		concert.setConcertName("테스트 콘서트");
		concert.setConcertDec("테스트 설명");
	}

	@Test
	@DisplayName("정상적인 콘서트 조회 시 콘서트 날짜 목록 반환")
	void testGetConcerts_Success_ReturnsConcertDates() {
		// given
		ConcertResponse response1 = new ConcertResponse();
		response1.setConcertId(concertId);
		response1.setConcertName("테스트 콘서트");
		response1.setConcertDate("20241225");

		ConcertResponse response2 = new ConcertResponse();
		response2.setConcertId(concertId);
		response2.setConcertName("테스트 콘서트");
		response2.setConcertDate("20241226");

		List<ConcertResponse> expectedResponses = Arrays.asList(response1, response2);

		when(concertRepository.findById(concertId)).thenReturn(Optional.of(concert));
		when(concertRepositoryImpl.findConcertDate(concertId)).thenReturn(expectedResponses);

		// when
		List<ConcertResponse> result = concertService.getConcerts(concertId);

		// then
		assertThat(result).isNotNull();
		assertThat(result).hasSize(2);
		assertThat(result).isEqualTo(expectedResponses);
		verify(concertRepository).findById(concertId);
		verify(concertRepositoryImpl).findConcertDate(concertId);
	}

	@Test
	@DisplayName("콘서트를 찾을 수 없으면 예외 발생")
	void testGetConcerts_ConcertNotFound_ThrowsException() {
		// given
		when(concertRepository.findById(concertId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> concertService.getConcerts(concertId))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("concert를 찾을수 없습니다");
		verify(concertRepository).findById(concertId);
		verify(concertRepositoryImpl, never()).findConcertDate(any());
	}
}
