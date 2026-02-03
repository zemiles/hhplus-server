package kr.hhplus.be.server.reservation.listener;

import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.event.PaymentCompletedEvent;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import kr.hhplus.be.server.reservation.port.SeatRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 결제 완료 이벤트 리스너 - 랭킹 업데이트
 * 
 * 결제 완료 후 콘서트가 매진되었는지 확인하고,
 * 매진이면 랭킹에 추가합니다.
 * 
 * 트랜잭션과 관심사를 분리하기 위해 이벤트로 처리됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRankingEventListener {

	private final SeatRepositoryPort seatRepositoryPort;
	private final ReservationRepositoryPort reservationRepositoryPort;
	private final ConcertRankingService concertRankingService;

	/**
	 * 결제 완료 이벤트를 수신하여 랭킹을 업데이트합니다.
	 * 
	 * @param event 결제 완료 이벤트
	 */
	@Async("taskExecutor")
	@EventListener
	public void handlePaymentCompleted(PaymentCompletedEvent event) {
		try {
			Long concertScheduleId = event.getConcertScheduleId();
			
			// 전체 좌석 개수 조회
			long totalSeats = seatRepositoryPort.countByConcertScheduleId(concertScheduleId);
			
			if (totalSeats == 0) {
				// 좌석이 없으면 매진 확인 불가
				log.debug("좌석이 없어 매진 확인 불가: concertScheduleId={}", concertScheduleId);
				return;
			}

			// 결제 완료된 예약 개수 조회
			long paidReservations = reservationRepositoryPort.countByConcertScheduleIdAndStatus(
					concertScheduleId, 
					ReservationStatus.PAID
			);

			// 모든 좌석이 결제 완료되었는지 확인
			if (paidReservations >= totalSeats) {
				// 매진! 랭킹에 추가
				concertRankingService.addSoldOutConcert(concertScheduleId);
				log.info("콘서트 매진: concertScheduleId={}, totalSeats={}, paidReservations={}", 
						concertScheduleId, totalSeats, paidReservations);
			}
		} catch (Exception e) {
			log.error("랭킹 업데이트 실패: concertScheduleId={}, paymentId={}", 
					event.getConcertScheduleId(), event.getPaymentId(), e);
			// 랭킹 업데이트 실패는 치명적이지 않으므로 예외를 다시 던지지 않음
		}
	}
}
