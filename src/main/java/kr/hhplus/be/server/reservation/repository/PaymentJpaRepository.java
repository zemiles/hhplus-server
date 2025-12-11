package kr.hhplus.be.server.reservation.repository;

import kr.hhplus.be.server.reservation.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
