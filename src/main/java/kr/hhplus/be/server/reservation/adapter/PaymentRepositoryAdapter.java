package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.reservation.domain.Payment;
import kr.hhplus.be.server.reservation.port.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {
	@Override
	public Payment save(Payment payment) {
		return payment;
	}

	@Override
	public Optional<Payment> findById(Long paymentId) {
		return Optional.empty();
	}

	@Override
	public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
		return Optional.empty();
	}
}
