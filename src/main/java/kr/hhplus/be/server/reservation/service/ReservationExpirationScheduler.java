package kr.hhplus.be.server.reservation.service;

import jakarta.transaction.Transactional;
import kr.hhplus.be.server.reservation.domain.ReservationStatus;
import kr.hhplus.be.server.reservation.port.ReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

	private final ReservationRepositoryPort reservationRepositoryPort;

	@Scheduled(fixedDelay = 60000)
	@Transactional
	public void expireReservations() {
		try {
			LocalDateTime now = LocalDateTime.now();

			//만료된 HOLD 상태 예약을 EXPIRED로 변경
			int expiredCount = reservationRepositoryPort.expireReservations(
					ReservationStatus.HOLD,
					ReservationStatus.EXPIRED,
					now
			);

			if(expiredCount > 0) {
				log.info("만료된 예약 {}개를 해제했습니다.", expiredCount);
			}

		} catch (Exception e) {
			log.error("예약 만료 처리 중 오류 발생", e);
		}
	}

}
