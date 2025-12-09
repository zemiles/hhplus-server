package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.point.domain.Wallet;
import kr.hhplus.be.server.reservation.port.WalletRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WalletRepositoryAdapter implements WalletRepositoryPort {

//	private final WalletRepositoryPort walletRepositoryPort;

	@Override
	public Optional<Wallet> findByUserId(Long userId) {
		return Optional.empty();
	}

	@Override
	public void deductBalance(Long walletId, BigDecimal amount) {

	}

	@Override
	public BigDecimal getBalance(Long walletId) {
		return null;
	}
}
