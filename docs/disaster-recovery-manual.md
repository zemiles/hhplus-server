# 장애 대응 매뉴얼

## 1. 개요

본 문서는 HangHaePlus 콘서트 예약 시스템의 **장애 대응 매뉴얼**입니다.  
부하 테스트 결과와 시스템 아키텍처를 기반으로 작성되었으며, 다음 두 가지 유형을 다룹니다.

| 유형 | 설명 | 예시 |
|------|------|------|
| **개선 어려운 포인트** | 아키텍처·비용 제약으로 완전 해결이 어려운 부분 | Redis 장애, DB 커넥션 풀 고갈, 외부 API 실패 |
| **예측 불가 포인트** | 사전에 예측하기 어려운 돌발 장애 | 네트워크 파티션, 디스크 풀, DDoS, 서드파티 버그 |

---

## 2. 부하 테스트 기반 현황

### 2.1 테스트 결과 요약 (load-test-results.md 기반)

| API | TPS | p95 | 에러율 | 비고 |
|-----|-----|-----|--------|------|
| GET /api/v1/ranking/soldout | **838.94/s** | 9.61ms | 0% | 300 VU, 117,566건 |
| GET /api/v1/reservation/possibility/1/dates | (측정값) | - | - | 80 CCU |
| POST /api/v1/reservation/dates/place | (측정값) | - | - | 50→100 CCU |

### 2.2 병목 지점 및 개선 사항 (적용 완료)

| 항목 | 기존 | 개선 후 (prod 프로파일) |
|------|------|-------------------------|
| HikariCP maximum-pool-size | 3 | 20 |
| Redis Lettuce max-active | 8 | 32 |
| TaskExecutor core/max/queue | 5/10/100 | 10/20/500 |
| ConcertRepositoryImpl 조인 | from(qConcert) + 잘못된 조인 | from(qSeat) + distinct |

**운영 배포 시**: `--spring.profiles.active=prod` 사용 권장.

---

## 3. 개선 어려운 포인트 대응 매뉴얼

### 3.1 Redis 장애

#### 3.1.1 영향 범위

- **분산락** (DistributedLockService): 좌석 예약 동시성 제어
- **랭킹** (ConcertRankingService): 매진 랭킹 조회
- **멱등성** (RedisEventIdempotencyAdapter): 결제/이벤트 중복 방지

#### 3.1.2 증상

- `RedisConnectionException`, `RedisCommandTimeoutException`
- 예약 API 5xx, 랭킹 API 5xx
- 분산락 획득 실패: `IllegalStateException: 락 획득에 실패했습니다`

#### 3.1.3 확인 절차

```bash
# Redis 연결 확인
redis-cli -h <host> -p 6379 ping

# Redis 메모리/클라이언트 수 확인
redis-cli info memory
redis-cli info clients
```

#### 3.1.4 대응 절차

| 단계 | 작업 | 비고 |
|------|------|------|
| 1 | Redis 프로세스/컨테이너 상태 확인 | `docker ps`, `systemctl status redis` |
| 2 | Redis 재시작 | 단일 인스턴스 시 서비스 일시 중단 |
| 3 | Redis Sentinel/Cluster 장애 노드 격리 | 클러스터 구성 시 |
| 4 | **폴백**: 랭킹 API → DB 기반 조회로 전환 | 코드 수정 또는 설정 플래그 필요 |
| 5 | **폴백**: 분산락 → DB 기반 락 또는 단일 인스턴스 락 | 장기 대응 |

#### 3.1.5 복구 후 점검

- 랭킹 데이터 재구성: 결제 완료 이벤트 재처리 또는 DB 기반 랭킹 스크립트 실행
- 멱등성 키 TTL(24h) 경과 후 중복 처리 가능성 검토

---

### 3.2 DB 커넥션 풀 고갈

#### 3.2.1 영향 범위

- 모든 DB 기반 API (예약, 결제, 좌석 조회 등)

#### 3.2.2 증상

- `HikariPool - Connection is not available`
- `java.sql.SQLTransientConnectionException`
- API 응답 지연 또는 5xx

#### 3.2.3 확인 절차

```sql
-- MySQL 현재 연결 수
SHOW STATUS LIKE 'Threads_connected';
SHOW PROCESSLIST;
```

```yaml
# application.yml - HikariCP 로깅
logging:
  level:
    com.zaxxer.hikari: DEBUG
```

#### 3.2.4 대응 절차

| 단계 | 작업 | 비고 |
|------|------|------|
| 1 | 장시간 실행 쿼리 확인 및 Kill | `SHOW PROCESSLIST` → `KILL <id>` |
| 2 | connection-timeout 확인 (기본 10s) | 대기 중인 요청이 풀 고갈 유발 |
| 3 | maximum-pool-size 임시 상향 | prod: 20 → 30 (DB 리소스 한도 내) |
| 4 | 애플리케이션 재시작 | 풀 초기화로 일시 완화 |
| 5 | **근본 원인**: 느린 쿼리, N+1, 트랜잭션 장시간 유지 등 분석 | 슬로우 쿼리 로그 확인 |

#### 3.2.5 복구 후 점검

- 슬로우 쿼리 로그 분석 및 인덱스/쿼리 튜닝
- 트랜잭션 범위 축소 검토

---

### 3.3 외부 API 실패 (데이터 플랫폼)

#### 3.3.1 영향 범위

- 결제 완료 후 데이터 플랫폼 전송 (이벤트 기반, 비동기)

#### 3.3.2 증상

- `RestTemplate` 타임아웃, 4xx/5xx
- 이벤트 리스너 로그: `데이터 플랫폼 전송 실패` (예외 비전파)

#### 3.3.3 확인 절차

```bash
# 데이터 플랫폼 API 헬스 체크
curl -X GET <데이터플랫폼_엔드포인트>/health
```

- 애플리케이션 로그: `PaymentDataPlatformKafkaConsumer`, `DataPlatformEventListener`

#### 3.3.4 대응 절차

| 단계 | 작업 | 비고 |
|------|------|------|
| 1 | 데이터 플랫폼 측 장애 여부 확인 | 담당팀 연락 |
| 2 | 네트워크/방화벽 이슈 확인 | `telnet`, `curl` |
| 3 | **Kafka DLQ 확인**: `payment-completed-dlq` | 재시도 3회 초과 메시지 |
| 4 | DLQ 메시지 수동 재처리 또는 배치 재전송 | event-compensation.md 참고 |
| 5 | **보상 전략**: event_outbox 테이블에 실패 건 기록 후 배치 재전송 (권장) | 미구현 시 추후 적용 |

#### 3.3.5 복구 후 점검

- DLQ 메시지 처리 완료 여부 확인
- 데이터 플랫폼 측 데이터 정합성 검증

---

### 3.4 Kafka Consumer 장애

#### 3.4.1 영향 범위

- 랭킹 업데이트 (PaymentRankingKafkaConsumer)
- 데이터 플랫폼 전송 (PaymentDataPlatformKafkaConsumer)

#### 3.4.2 증상

- Consumer Lag 증가
- 랭킹 API 응답이 최신 결제 반영 안 됨
- 데이터 플랫폼 전송 누락

#### 3.4.3 확인 절차

```bash
# Consumer Group Lag 확인
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group payment-ranking-consumer-group
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group payment-data-platform-consumer-group
```

#### 3.4.4 대응 절차

| 단계 | 작업 | 비고 |
|------|------|------|
| 1 | Consumer 프로세스 상태 확인 | 로그, 스레드 덤프 |
| 2 | Consumer 재시작 | Lag 소진 대기 |
| 3 | 파티션 수 대비 Consumer 인스턴스 수 확인 | 인스턴스 추가로 처리량 확장 |
| 4 | **DLQ** (`payment-completed-dlq`) 수동 처리 | kafka-design.md 참고 |
| 5 | 멱등성으로 인한 중복 처리 안전성 확인 | idempotencyKey 기반 |

#### 3.4.5 복구 후 점검

- Lag 0 수렴 확인
- 랭킹/데이터 플랫폼 데이터 정합성 검증

---

### 3.5 분산락 경합 (Lock Contention)

#### 3.5.1 영향 범위

- 동일 좌석에 대한 동시 예약 시도

#### 3.5.2 증상

- `IllegalStateException: 락 획득에 실패했습니다. lockKey: seat:xxx`
- 예약 API 5xx 또는 409

#### 3.5.3 확인 절차

- Redis `INFO stats` : `keyspace_hits`, `keyspace_misses`
- 애플리케이션 로그: `락 획득 실패` 빈도

#### 3.5.4 대응 절차

| 단계 | 작업 | 비고 |
|------|------|------|
| 1 | Redis 응답 지연 확인 | `redis-cli --latency` |
| 2 | Redis 풀 크기 확인 (prod: max-active 32) | 부족 시 상향 |
| 3 | `MAX_WAIT_TIME_MS`(5s), `LOCK_RETRY_INTERVAL_MS`(100ms) 조정 | 코드 상수, 배포 필요 |
| 4 | **근본 원인**: 인기 좌석 동시 요청 → 사용자에게 "잠시 후 재시도" 안내 | UX 개선 |

---

## 4. 예측 불가 포인트 대응 매뉴얼

### 4.1 네트워크 파티션 (Split-Brain)

#### 4.1.1 설명

- 서버 간 네트워크 단절로 일부 노드만 통신 가능한 상태
- Redis Cluster, Kafka Cluster에서 발생 가능

#### 4.1.2 증상

- 일부 API만 응답, 일부 타임아웃
- Redis/Kafka 연결 불안정

#### 4.1.3 대응 절차

| 단계 | 작업 |
|------|------|
| 1 | 네트워크 구간 확인 (VPC, 서브넷, 보안 그룹) |
| 2 | 장애 구간 격리 또는 복구 (네트워크팀 협조) |
| 3 | Redis/Kafka 클러스터 리더 재선출 대기 |
| 4 | 애플리케이션 재시작으로 연결 재수립 |
| 5 | 데이터 정합성 검증 (Redis, DB) |

---

### 4.2 디스크 풀 (Disk Full)

#### 4.2.1 설명

- 로그, DB, Kafka 데이터 등으로 디스크 사용량 100% 도달

#### 4.2.2 증상

- `NoSpaceLeftOnDevice`
- DB 쓰기 실패, Kafka Broker 장애, 로그 기록 실패

#### 4.2.3 대응 절차

| 단계 | 작업 |
|------|------|
| 1 | `df -h`, `du -sh`로 사용량 확인 |
| 2 | 불필요 로그/임시 파일 삭제 (주의: 애플리케이션 로그는 보존) |
| 3 | 로그 로테이션 설정 확인 (`logback-spring.xml`) |
| 4 | 디스크 확장 또는 스토리지 이전 |
| 5 | 서비스 재시작 |

---

### 4.3 메모리 누수 / OOM

#### 4.3.1 설명

- 장시간 운영 시 힙 메모리 부족으로 `OutOfMemoryError`

#### 4.3.2 증상

- `java.lang.OutOfMemoryError: Java heap space`
- 애플리케이션 비정상 종료, 502/503

#### 4.3.3 대응 절차

| 단계 | 작업 |
|------|------|
| 1 | 힙 덤프 수집 (`-XX:+HeapDumpOnOutOfMemoryError`) |
| 2 | 애플리케이션 재시작 (임시 복구) |
| 3 | 힙 덤프 분석 (Eclipse MAT, VisualVM) |
| 4 | 누수 원인 수정 (캐시 미해제, 대용량 조회 등) |
| 5 | JVM 힙 크기 조정 (`-Xmx`, `-Xms`) |

---

### 4.4 DDoS / 비정상 트래픽

#### 4.4.1 설명

- 짧은 시간에 대량 요청으로 서비스 마비

#### 4.4.2 증상

- CPU/메모리/네트워크 사용률 급증
- 정상 사용자 API 지연 또는 5xx

#### 4.4.3 대응 절차

| 단계 | 작업 |
|------|------|
| 1 | WAF/CDN(Cloudflare, AWS WAF) 차단 규칙 적용 |
| 2 | Rate Limiting 적용 (API Gateway, Spring) |
| 3 | 의심 IP/User-Agent 차단 |
| 4 | 스케일 아웃 (인스턴스 추가) |
| 5 | 트래픽 패턴 분석 후 화이트리스트/블랙리스트 정교화 |

---

### 4.5 서드파티 라이브러리 버그

#### 4.5.1 설명

- Spring, Hibernate, Redis, Kafka 등 의존성 내부 버그

#### 4.5.2 증상

- 특정 시나리오에서만 발생하는 예외, 성능 저하
- 스택 트레이스에 서드파티 패키지 포함

#### 4.5.3 대응 절차

| 단계 | 작업 |
|------|------|
| 1 | 예외 로그, 스택 트레이스 수집 |
| 2 | 해당 라이브러리 이슈 트래커 검색 (GitHub Issues 등) |
| 3 | 패치 버전으로 업그레이드 (테스트 환경 선행) |
| 4 | 임시 워크어라운드 적용 (설정 변경, 코드 우회) |
| 5 | 공급업체 지원 채널 문의 |

---

### 4.6 예기치 않은 데이터 정합성 이슈

#### 4.6.1 설명

- 비즈니스 로직 버그, 레이스 컨디션으로 인한 데이터 불일치

#### 4.6.2 증상

- 중복 예약, 결제/예약 상태 불일치, 랭킹 순위 오류

#### 4.6.3 대응 절차

| 단계 | 작업 |
|------|------|
| 1 | 영향 범위 파악 (특정 기간, 특정 콘서트 등) |
| 2 | DB/Redis 데이터 수동 검증 및 보정 스크립트 작성 |
| 3 | 보정 스크립트 스테이징 검증 후 운영 적용 |
| 4 | 근본 원인 수정 (락, 트랜잭션, 멱등성 강화) |
| 5 | 재발 방지 모니터링/알림 설정 |

---

## 5. 공통 운영 권장사항

### 5.1 모니터링 지표

| 지표 | 임계치 | 도구 예시 |
|------|--------|-----------|
| API p95 응답 시간 | > 500ms | Prometheus, Datadog |
| 에러율 | > 1% | APM, 로그 |
| HikariCP Active Connections | > pool-size 80% | Micrometer |
| Redis 연결 수 | > max-active 80% | Redis INFO |
| Kafka Consumer Lag | > 1000 | Kafka Monitor |
| CPU / Memory | > 80% | CloudWatch, Grafana |

### 5.2 알림 설정

- 위 지표 임계치 초과 시 PagerDuty, Slack 등 알림
- 장애 대응 담당자 로테이션 및 연락망 문서화

### 5.3 정기 점검

- 주 1회: 로그, 에러율, 슬로우 쿼리 리뷰
- 월 1회: 부하 테스트 재실행, 장애 대응 훈련

---

## 6. 관련 문서

| 문서 | 내용 |
|------|------|
| [load-test-results.md](./load-test-results.md) | 부하 테스트 결과 |
| [load-test-plan.md](./load-test-plan.md) | 부하 테스트 계획 |
| [event-compensation.md](./event-compensation.md) | 이벤트 보상 전략 |
| [kafka-design.md](./kafka-design.md) | Kafka 설계 및 DLQ 정책 |

---

**작성일**: 2025-03
