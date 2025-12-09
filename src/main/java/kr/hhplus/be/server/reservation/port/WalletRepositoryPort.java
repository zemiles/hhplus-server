package kr.hhplus.be.server.reservation.port;

import kr.hhplus.be.server.point.domain.Wallet;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface WalletRepositoryPort {
	Optional<Wallet> findByUserId(Long userId);
	void deductBalance(Long walletId, BigDecimal amount);
	BigDecimal getBalance(Long walletId);
}
