# 부하 테스트 결과 보고서

## 1. 테스트 환경

| 항목 | 내용 |
|------|------|
| 테스트 일시 | 2025-03-01 |
| 애플리케이션 | Spring Boot 3.4.1, Java 17 |
| 인프라 | MySQL 8.0, Redis 7 |
| 부하 도구 | k6 |
| 실행 위치 | 로컬 / Docker |

---

## 2. 테스트 스크립트

### 2.1 사용 스크립트

| 스크립트 | 대상 | 부하 프로파일 |
|----------|------|---------------|
| `scripts/load-test/ranking-load.js` | GET /api/v1/ranking/soldout | 100→300 CCU |
| `scripts/load-test/possibility-load.js` | GET /api/v1/reservation/possibility/1/dates | 80 CCU |
| `scripts/load-test/reservation-load.js` | POST /api/v1/reservation/dates/place | 50→100 CCU |
| `scripts/load-test/scenario-integrated.js` | 통합 시나리오 | 50 CCU |

### 2.2 실행 명령

```bash
# 랭킹 조회 부하 테스트
k6 run scripts/load-test/ranking-load.js

# BASE_URL 지정 (Docker 앱 대상)
k6 run -e BASE_URL=http://host.docker.internal:8080 scripts/load-test/ranking-load.js
```

---

## 3. 실행 결과

### 3.1 랭킹 조회 (ranking-load.js)

#### 3.1.1 2026-03-01 실행 (h2+prod 프로파일, 병목 개선 적용 후)

**환경**: H2+prod 프로파일, Redis(Docker), HikariCP 20, Redis pool 32, TaskExecutor 10/20/500

| 실행 | 총 요청 | TPS | p95 | 에러율 |
|------|---------|-----|-----|--------|
| 1차 | 118,082 | 842.48/s | 8.05ms | 0% |
| 2차 (재테스트) | 118,330 | **844.08/s** | **6.85ms** | 0% |

#### 3.1.2 2025-03-01 실행 (h2 프로파일, 개선 전)

**환경**: H2 프로파일, 로컬 Redis, Spring Boot 3.4.1

| 지표 | 개선 전 | 개선 후 (prod) |
|------|---------|----------------|
| TPS | 838.94/s | **844/s** (변화 미미) |
| p95 | 9.61ms | **6.85ms** (↓29%) |
| 에러율 | 0.00% | 0.00% |

**TPS가 크게 오르지 않은 이유**: 랭킹 API는 Redis만 사용(DB 미사용)하며, Redis ZRANGE는 이미 매우 빠릅니다. HikariCP·TaskExecutor 튜닝은 DB/비동기 처리에만 영향을 주어 랭킹 API에는 거의 영향이 없습니다. 또한 k6 스크립트의 `sleep(0.2)`로 1 VU당 초당 약 5회 요청으로 제한되어 있어, TPS 상한이 스크립트에 의해 결정됩니다.

**TPS를 낮추고 싶을 때** (부하 완화): `sleep(0.2)` → `sleep(0.5)` 등으로 늘리거나, stages의 target VU를 줄이면 됩니다.

### 3.2 예약 가능 일정 (possibility-load.js)

- **참고**: Windows 환경에서 연속 실행 시 소켓 고갈(`connectex: Only one usage of each socket address...`) 발생 가능. 테스트 간 충분한 대기(예: 1분) 후 실행 권장.

### 3.3 좌석 예약 (reservation-load.js)

```
     (실행 결과 복사)
```

### 3.4 리소스 제한 실험

| 스펙 | CPU | Memory | p95 | TPS | 에러율 |
|------|-----|--------|-----|-----|--------|
| Low | 0.5 | 512MB | (ms) | (/) | (%) |
| Medium | 1 | 1GB | (ms) | (/) | (%) |
| High | 2 | 2GB | (ms) | (/) | (%) |

---

## 4. 배포 스펙 권장안

| 환경 | CPU | Memory | 비고 |
|------|-----|--------|------|
| 개발 | 0.5 | 512MB | 단일 사용자 |
| 스테이징 | 1 | 1GB | 소규모 부하 |
| 운영 (최소) | 1 | 1GB | 목표 CCU 50 이하 |
| 운영 (권장) | 2 | 2GB | 목표 CCU 100~200 |

---

## 5. 부하 테스트 실행 방법

### 5.1 사전 준비

1. **k6 설치**: https://k6.io/docs/getting-started/installation/
2. **애플리케이션 기동**: `./gradlew bootRun --args='--spring.profiles.active=local'`
3. **MySQL, Redis**: `docker-compose up -d`

### 5.2 실행

```bash
# 랭킹 (시드 데이터 불필요)
k6 run scripts/load-test/ranking-load.js

# 예약 가능 일정 (콘서트 데이터 필요)
k6 run scripts/load-test/possibility-load.js

# 좌석 예약 (콘서트, 좌석, 사용자 시드 데이터 필요)
k6 run scripts/load-test/reservation-load.js
```

### 5.3 리소스 제한 실험

```bash
# docker-compose.load-test.yaml에서 deploy.resources.limits 수정 후
docker-compose -f docker-compose.load-test.yaml up -d app

# 부하 테스트 실행
k6 run --vus 50 --duration 60s scripts/load-test/ranking-load.js
```

---

**작성일**: 2025-03
