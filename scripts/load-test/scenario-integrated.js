/**
 * 통합 시나리오 부하 테스트
 * 
 * 흐름: 예약 가능 일정 조회 → 좌석 예약 → 결제 → 랭킹 조회
 * 
 * 실행: k6 run scripts/load-test/scenario-integrated.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '60s', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    errors: ['rate<0.15'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const vuId = __VU;
  const iterId = __ITER;
  const userId = (vuId % 100) + 1;
  const seatId = (iterId % 100) + 1;
  const idempotencyKey = `integrated-${vuId}-${iterId}-${Date.now()}`;

  // 1. 예약 가능 일정 조회
  let res = http.get(`${BASE_URL}/api/v1/reservation/possibility/1/dates`);
  if (!check(res, { '1. possibility status 200': (r) => r.status === 200 })) {
    errorRate.add(1);
    sleep(1);
    return;
  }

  // 2. 좌석 예약
  res = http.post(
    `${BASE_URL}/api/v1/reservation/dates/place?concertId=1&date=20250315&place=${seatId}&userId=${userId}`,
    null,
    { headers: { 'Idempotency-Key': idempotencyKey } }
  );

  if (res.status !== 200 && res.status !== 400) {
    errorRate.add(1);
    sleep(1);
    return;
  }

  // 3. 예약 성공 시에만 결제
  let reservationId = null;
  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      reservationId = body.reservationId;
    } catch (e) {
      // 응답 파싱 실패
    }
  }

  if (reservationId) {
    const payKey = `pay-${idempotencyKey}`;
    res = http.post(
      `${BASE_URL}/api/v1/reservation/${reservationId}/payment`,
      null,
      { headers: { 'Idempotency-Key': payKey } }
    );
    check(res, { '3. payment status 200': (r) => r.status === 200 }) || errorRate.add(1);
  }

  // 4. 랭킹 조회
  res = http.get(`${BASE_URL}/api/v1/ranking/soldout?limit=10`);
  check(res, { '4. ranking status 200': (r) => r.status === 200 }) || errorRate.add(1);

  sleep(0.5);
}
