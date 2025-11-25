package kr.hhplus.be.server.concert.repository;

import kr.hhplus.be.server.concert.domain.Concert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConcertRepository extends JpaRepository<Concert, Long> {

}
