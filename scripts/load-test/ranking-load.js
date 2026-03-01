/**
 * 랭킹 조회 부하 테스트
 * 
 * 대상: GET /api/v1/ranking/soldout
 * 시나리오: 매진 직후 100~500 CCU 동시 조회
 * 
 * 실행: k6 run scripts/load-test/ranking-load.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '20s', target: 100 },
    { duration: '40s', target: 100 },
    { duration: '20s', target: 300 },
    { duration: '40s', target: 300 },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // p95 < 500ms
    errors: ['rate<0.01'],               // 에러율 < 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const limit = Math.floor(Math.random() * 20) + 1;
  const res = http.get(`${BASE_URL}/api/v1/ranking/soldout?limit=${limit}`);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
  });
  errorRate.add(!success);

  sleep(0.2);
}
