package kr.hhplus.be.server.reservation.kafka;

import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.event.PaymentCompletedMessage;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.port.SeatRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 결제 완료 Kafka Consumer - 매진 랭킹 업데이트
 *
 * payment-completed 토픽을 구독하여 매진 여부를 확인하고 랭킹에 반영합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.event.provider", havingValue = "kafka", matchIfMissing = true)
public class PaymentRankingKafkaConsumer {

	private final SeatRepositoryPort seatRepositoryPort;
	private final ReservationRepositoryPort reservationRepositoryPort;
	private final ConcertRankingService concertRankingService;

	@KafkaListener(
			topics = "${spring.kafka.topic.payment-completed:payment-completed}",
			groupId = "payment-ranking-consumer-group"
	)
	public void consume(PaymentCompletedMessage message) {
		try {
			Long concertScheduleId = message.getConcertScheduleId();

			long totalSeats = seatRepositoryPort.countByConcertScheduleId(concertScheduleId);
			if (totalSeats == 0) {
				log.debug("좌석이 없어 매진 확인 불가: concertScheduleId={}", concertScheduleId);
				return;
			}

			long paidReservations = reservationRepositoryPort.countByConcertScheduleIdAndStatus(
					concertScheduleId,
					ReservationStatus.PAID
			);

			if (paidReservations >= totalSeats) {
				concertRankingService.addSoldOutConcert(concertScheduleId);
				log.info("콘서트 매진 (Kafka): concertScheduleId={}, totalSeats={}, paidReservations={}",
						concertScheduleId, totalSeats, paidReservations);
			}
		} catch (Exception e) {
			log.error("랭킹 업데이트 실패 (Kafka): concertScheduleId={}, paymentId={}",
					message.getConcertScheduleId(), message.getPaymentId(), e);
		}
	}
}
