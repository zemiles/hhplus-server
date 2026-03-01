package kr.hhplus.be.server.reservation.port;

/**
 * 이벤트 처리 멱등성 체크 포트
 *
 * 중복 발행/재시도 시 동일 이벤트의 중복 처리를 방지합니다.
 * idempotencyKey 기준으로 이미 처리된 이벤트인지 확인합니다.
 */
public interface EventIdempotencyPort {

	/**
	 * 해당 이벤트를 처리해도 되는지 확인합니다 (처리 권한 획득 시도).
	 *
	 * @param idempotencyKey 멱등성 키
	 * @param processorType  처리 유형 (예: "data-platform", "ranking")
	 * @return 처음 처리하는 경우 true (처리 진행), 이미 처리된 경우 false (스킵)
	 */
	boolean tryAcquireProcessing(String idempotencyKey, String processorType);
}
