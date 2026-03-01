/**
 * 좌석 예약(홀드) 부하 테스트
 * 
 * 대상: POST /api/v1/reservation/dates/place
 * 시나리오: 동시 50~100명이 좌석 선점 시도
 * 
 * 실행: k6 run scripts/load-test/reservation-load.js
 * 옵션: k6 run -e BASE_URL=http://localhost:8080 scripts/load-test/reservation-load.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 50 },   // 부하 증가
    { duration: '60s', target: 50 },   // 유지
    { duration: '30s', target: 100 },  // 피크
    { duration: '60s', target: 100 },  // 피크 유지
    { duration: '30s', target: 0 },    // 감소
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],  // p95 < 2초
    errors: ['rate<0.1'],                 // 에러율 < 10%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // seatId 1~100 중 랜덤, userId 1~100 중 랜덤
  const seatId = Math.floor(Math.random() * 100) + 1;
  const userId = Math.floor(Math.random() * 100) + 1;
  const idempotencyKey = `load-test-${__VU}-${__ITER}-${Date.now()}`;

  const params = {
    concertId: 1,
    date: '20250315',
    place: String(seatId),
    userId: userId,
  };

  const res = http.post(
    `${BASE_URL}/api/v1/reservation/dates/place?concertId=${params.concertId}&date=${params.date}&place=${params.place}&userId=${params.userId}`,
    null,
    {
      headers: {
        'Idempotency-Key': idempotencyKey,
        'Content-Type': 'application/json',
      },
    }
  );

  const success = check(res, {
    'status is 200 or 400': (r) => r.status === 200 || r.status === 400, // 400 = 이미 예약됨
  });
  errorRate.add(!success);

  sleep(0.5);
}
