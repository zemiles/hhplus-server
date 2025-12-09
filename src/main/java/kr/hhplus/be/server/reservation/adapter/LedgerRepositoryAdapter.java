package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.point.domain.Ledger;
import kr.hhplus.be.server.reservation.port.LedgerRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LedgerRepositoryAdapter implements LedgerRepositoryPort {
	@Override
	public Ledger save(Ledger ledger) {
		return null;
	}

	@Override
	public Optional<Ledger> findByIdempotencyKey(String idempotencyKey) {
		return Optional.empty();
	}
}
