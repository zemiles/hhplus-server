package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.point.domain.Ledger;
import kr.hhplus.be.server.point.repository.LedgerRepository;
import kr.hhplus.be.server.reservation.port.LedgerRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LedgerRepositoryAdapter implements LedgerRepositoryPort {

	private final LedgerRepository ledgerRepository;

	@Override
	public Ledger save(Ledger ledger) {
		return ledgerRepository.save(ledger);
	}

	@Override
	public Optional<Ledger> findByIdempotencyKey(String idempotencyKey) {
		// Ledger 엔티티에 idempotencyKey 필드가 없으므로 빈 Optional 반환
		// 필요시 Ledger 엔티티에 idempotencyKey 필드를 추가하고 구현해야 함
		return Optional.empty();
	}
}
