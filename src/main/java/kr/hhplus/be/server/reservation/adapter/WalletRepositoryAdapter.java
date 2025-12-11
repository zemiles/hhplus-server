package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.point.domain.Wallet;
import kr.hhplus.be.server.point.repository.WalletRepository;
import kr.hhplus.be.server.reservation.port.WalletRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WalletRepositoryAdapter implements WalletRepositoryPort {

	private final WalletRepository walletRepository;

	@Override
	public Optional<Wallet> findByUserId(Long userId) {
		return walletRepository.findByUserId(userId);
	}

	@Override
	@Transactional
	public void deductBalance(Long walletId, BigDecimal amount) {
		walletRepository.deductBalance(walletId, amount);
	}

	@Override
	public BigDecimal getBalance(Long walletId) {
		return walletRepository.getBalance(walletId);
	}
}
