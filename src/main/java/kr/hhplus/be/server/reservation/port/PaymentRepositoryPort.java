package kr.hhplus.be.server.reservation.port;

import kr.hhplus.be.server.reservation.domain.Payment;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepositoryPort {
	Payment save(Payment payment);
	Optional<Payment> findById(Long paymentId);
	Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
