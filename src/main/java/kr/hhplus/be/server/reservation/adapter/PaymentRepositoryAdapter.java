package kr.hhplus.be.server.reservation.adapter;

import kr.hhplus.be.server.reservation.domain.Payment;
import kr.hhplus.be.server.reservation.port.PaymentRepositoryPort;
import kr.hhplus.be.server.reservation.repository.PaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {

	private final PaymentJpaRepository paymentJpaRepository;

	@Override
	public Payment save(Payment payment) {
		return paymentJpaRepository.save(payment);
	}

	@Override
	public Optional<Payment> findById(Long paymentId) {
		return paymentJpaRepository.findById(paymentId);
	}

	@Override
	public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
		return paymentJpaRepository.findByIdempotencyKey(idempotencyKey);
	}
}
