package kr.hhplus.be.server.point.repository;

import kr.hhplus.be.server.point.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
	@Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
	Optional<Wallet> findByUserId(@Param("userId") Long userId);

	@Query("SELECT w.balanceCents FROM Wallet w WHERE w.id = :walletId")
	BigDecimal getBalance(@Param("walletId") Long walletId);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE Wallet w SET w.balanceCents = w.balanceCents - :amount WHERE w.id = :walletId")
	void deductBalance(@Param("walletId") Long walletId, @Param("amount") BigDecimal amount);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE Wallet w SET w.balanceCents = w.balanceCents - :amount " +
			"WHERE w.id = :walletId AND w.balanceCents >= :amount")
	int deductBalanceIfSufficient(@Param("walletId") Long walletId, @Param("amount") BigDecimal amount);
}
