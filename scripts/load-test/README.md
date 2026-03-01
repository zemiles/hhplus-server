# 부하 테스트 스크립트

## 사전 요구사항

- **k6** 설치: https://k6.io/docs/getting-started/installation/
  - Windows: `choco install k6`
  - Mac: `brew install k6`
  - Linux: `sudo gpg -k && sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69 && echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list && sudo apt-get update && sudo apt-get install k6`

## 환경 준비

1. **애플리케이션 기동**
   ```bash
   # MySQL, Redis 실행
   docker-compose up -d

   # 애플리케이션 실행 (로컬)
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

2. **시드 데이터 (선택)**
   - 예약/결제 테스트 시 콘서트, 좌석, 사용자 데이터 필요
   - `seed-data.sql` 참고하여 DB에 데이터 삽입
   - 또는 Hibernate ddl-auto: update로 스키마 생성 후 수동 삽입

## 스크립트 실행

```bash
# 기본 (localhost:8080)
k6 run scripts/load-test/ranking-load.js

# BASE_URL 지정
k6 run -e BASE_URL=http://localhost:8080 scripts/load-test/ranking-load.js

# 결과를 JSON/HTML로 저장
k6 run --out json=result.json scripts/load-test/ranking-load.js
k6 run --out html=report.html scripts/load-test/ranking-load.js
```

## 스크립트 목록

| 스크립트 | 대상 API | 설명 |
|----------|----------|------|
| `ranking-load.js` | GET /api/v1/ranking/soldout | 랭킹 조회 부하 (시드 데이터 불필요) |
| `possibility-load.js` | GET /api/v1/reservation/possibility/{id}/dates | 예약 가능 일정 조회 |
| `reservation-load.js` | POST /api/v1/reservation/dates/place | 좌석 예약 (시드 데이터 필요) |
| `scenario-integrated.js` | 통합 시나리오 | 조회→예약→결제→랭킹 |

## 리소스 제한 실험

```bash
# Docker Compose로 앱 리소스 제한 후 부하 테스트
docker-compose -f docker-compose.load-test.yaml up -d app

# docker-compose.load-test.yaml에서 deploy.resources.limits 조절
# cpus: '0.5', memory: 512M  (Low)
# cpus: '1', memory: 1G      (Medium)
# cpus: '2', memory: 2G      (High)
```
