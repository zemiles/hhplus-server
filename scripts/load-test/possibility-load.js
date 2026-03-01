/**
 * 예약 가능 일정 조회 부하 테스트
 * 
 * 대상: GET /api/v1/reservation/possibility/{concert_id}/dates
 * 시나리오: 콘서트 선택 시 빈번한 조회
 * 
 * 실행: k6 run scripts/load-test/possibility-load.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 80 },
    { duration: '60s', target: 80 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    errors: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const concertId = 1;
  const res = http.get(`${BASE_URL}/api/v1/reservation/possibility/${concertId}/dates`);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
  });
  errorRate.add(!success);

  sleep(0.3);
}
