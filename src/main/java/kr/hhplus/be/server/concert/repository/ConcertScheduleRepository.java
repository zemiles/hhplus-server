package kr.hhplus.be.server.concert.repository;

import kr.hhplus.be.server.concert.domain.ConcertSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConcertScheduleRepository extends JpaRepository<ConcertSchedule, Long> {
}
