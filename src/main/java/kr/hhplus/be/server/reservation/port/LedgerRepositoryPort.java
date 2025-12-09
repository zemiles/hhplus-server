package kr.hhplus.be.server.reservation.port;

import kr.hhplus.be.server.point.domain.Ledger;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LedgerRepositoryPort {
	Ledger save(Ledger ledger);
	Optional<Ledger> findByIdempotencyKey(String idempotencyKey);
}
