package kr.hhplus.be.server.concert.repository.impl;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import kr.hhplus.be.server.concert.common.SeatStatus;
import kr.hhplus.be.server.concert.domain.QConcert;
import kr.hhplus.be.server.concert.domain.QConcertSchedule;
import kr.hhplus.be.server.concert.domain.QSeat;
import kr.hhplus.be.server.concert.dto.ConcertResponse;
import kr.hhplus.be.server.concert.repository.ConcertCustomRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ConcertRepositoryImpl implements ConcertCustomRepository {

	private final JPAQueryFactory jpaQueryFactory;

	ConcertRepositoryImpl(JPAQueryFactory jpaQueryFactory) {
		this.jpaQueryFactory = jpaQueryFactory;
	}

	private final QConcert qConcert = QConcert.concert;
	private final QConcertSchedule qConcertSchedule = QConcertSchedule.concertSchedule;
	private final QSeat qSeat = QSeat.seat;

	@Override
	public List<ConcertResponse> findConcertDate(Long concertId) {
		return jpaQueryFactory.select(Projections.constructor(ConcertResponse.class,
					qConcert.id,
					qConcert.concertName,
				    qConcert.concertStatus,
				    qConcertSchedule.concertDate,
				    qConcertSchedule.concertTime
				)).from(qConcert)
				.join(qSeat.concertSchedule, qConcertSchedule)
				.join(qConcertSchedule.concert, qConcert)
				.where(qConcert.id.eq(concertId).and(qSeat.seatStatus.eq(SeatStatus.NON_RESERVATION)))
				.fetch();
	}
}
