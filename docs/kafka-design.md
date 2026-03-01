# Kafka 기반 예약/결제 이벤트 전달 설계 문서

## 1. 개요

### 1.1 목적

기존 **Spring Application Event** 기반 이벤트 전달을 **Kafka**로 전환하여, 다음과 같은 목표를 달성합니다.

- 대용량 트래픽 시 결제 완료 이벤트의 안정적 전달
- 랭킹, 데이터 플랫폼 등 여러 Consumer의 독립적 소비
- 이벤트 유실 방지 및 재처리 가능

### 1.2 적용 범위

- **결제 완료 이벤트** → `payment-completed` 토픽
- **Producer**: ProcessPaymentUseCase (결제 완료 시)
- **Consumer 1**: PaymentRankingKafkaConsumer (매진 랭킹 업데이트)
- **Consumer 2**: PaymentDataPlatformKafkaConsumer (데이터 플랫폼 전송)

---

## 2. 카프카를 사용하면 좋은 이유

| 구분 | Spring Event | Kafka |
|------|--------------|-------|
| **범위** | 단일 JVM | 분산 시스템 전역 |
| **영속성** | 메모리 (유실 가능) | 디스크 저장 (영속) |
| **재처리** | 불가 | Offset 기반 재처리 가능 |
| **확장** | 단일 서버 | 파티션/브로커 수평 확장 |
| **Consumer 확장** | 동일 프로세스 | 별도 서비스로 분리 가능 |

**결제 완료 이벤트**는 랭킹, 데이터 플랫폼 등 여러 목적으로 사용되며, 대규모 트래픽 시에도 유실 없이 전달되어야 합니다. Kafka를 사용하면 이 요구사항을 충족할 수 있습니다.

---

## 3. 비즈니스 시퀀스 다이어그램

```
[Client]     [API]      [ProcessPaymentUseCase]  [DB]   [Kafka]     [RankingConsumer] [DataPlatformConsumer]
   |           |                    |              |         |                 |                    |
   |--결제 요청->|                    |              |         |                 |                    |
   |           |--execute()--------->|              |         |                 |                    |
   |           |                    |--트랜잭션 시작->|         |                 |                    |
   |           |                    |--예약/결제 저장->|        |                 |                    |
   |           |                    |--트랜잭션 커밋->|        |                 |                    |
   |           |                    |--publish()--->|         |                 |                    |
   |           |                    |               |         |<--메시지 발행----|                    |
   |           |                    |<--반환--------|         |                 |                    |
   |           |<--응답-------------|               |         |                 |                    |
   |           |                    |               |         |-----구독-------->|                    |
   |           |                    |               |         |-----구독----------------------------->|
   |           |                    |               |         |                 |--매진 시 랭킹 추가  |
   |           |                    |               |         |                 |--데이터 플랫폼 전송 |
```

---

## 4. Kafka 구성

### 4.1 토픽

| 토픽명 | 파티션 수 | replication | 용도 |
|--------|-----------|-------------|------|
| payment-completed | 3 | 1 (로컬) | 결제 완료 이벤트 |

### 4.2 Consumer Group

| Consumer Group | 역할 |
|----------------|------|
| payment-ranking-consumer-group | 매진 랭킹 업데이트 |
| payment-data-platform-consumer-group | 데이터 플랫폼 전송 |

### 4.3 메시지 포맷 (PaymentCompletedMessage)

```json
{
  "paymentId": 1,
  "userId": 100,
  "reservationId": 1,
  "concertScheduleId": 10,
  "totalAmountCents": 80000,
  "idempotencyKey": "uuid",
  "timestamp": 1709123456789
}
```

### 4.4 파티션 키

- **Key**: `reservationId` (같은 예약의 이벤트는 같은 파티션)
- **목적**: 같은 예약에 대한 순서 보장

---

## 5. 대용량 트래픽 지점 및 개선

### 5.1 트래픽이 집중되는 지점

1. **콘서트 오픈 직후**  
   - 대량의 예약 요청 → 결제 요청 집중

2. **인기 콘서트 매진 시점**  
   - 매진과 동시에 여러 결제 완료 이벤트 발생

3. **랭킹 API 호출**  
   - 매진 직후 빠른 매진 랭킹 조회 증가

### 5.2 Kafka 활용 개선 방향

| 지점 | 기존 | Kafka 적용 후 |
|------|------|---------------|
| 결제 완료 후 랭킹/데이터플랫폼 | 동기적 리스너 호출 (비동기 @Async) | Kafka 발행 후 즉시 반환, Consumer가 비동기 처리 |
| 이벤트 유실 | 서버 장애 시 유실 가능 | Kafka에 영속 저장, 재시작 후 재처리 |
| Consumer 확장 | 단일 인스턴스에서 처리 | Consumer 인스턴스 추가로 처리량 확장 |
| 데이터 플랫폼 | API 호출 실패 시 재시도 어려움 | Offset 유지로 실패 시 재처리 가능 |

---

## 6. 구현 요약

- `PaymentEventPublisherPort`: 이벤트 발행 인터페이스
- `KafkaPaymentEventPublisher`: Kafka Producer 구현
- `SpringEventPaymentEventPublisher`: Spring Event 구현 (테스트/폴백)
- `PaymentRankingKafkaConsumer`, `PaymentDataPlatformKafkaConsumer`: Kafka Consumer

### 6.1 프로파일별 동작

| 프로파일 | app.event.provider | 사용 구현체 |
|----------|-------------------|-------------|
| local, default | kafka (기본) | KafkaPaymentEventPublisher |
| h2 (테스트) | spring-event | SpringEventPaymentEventPublisher |

---

## 7. 소비자 장애·중복 처리 정책

### 7.1 재시도 정책

| 항목 | 설정값 | 설명 |
|------|--------|------|
| 최대 재시도 횟수 | 3회 | Consumer 처리 실패 시 최대 3회 재시도 |
| Backoff | Exponential (1s, 2s, 4s) | 재시도 간격을 지수적으로 증가 |
| 재시도 대상 | 일시적 오류 (DB 연결 끊김, 네트워크 타임아웃 등) | 영구적 오류는 DLQ로 이동 |

### 7.2 Dead Letter Queue (DLQ)

- **토픽**: `payment-completed-dlq`
- **이동 조건**: 최대 재시도 횟수 초과 시
- **처리**: DLQ 메시지는 수동 검토 후 재처리 또는 폐기
- **메타데이터**: 원본 메시지 + 실패 원인 + 타임스탬프 보존

### 7.3 멱등성(Idempotency) 활용

- **Producer**: `Idempotency-Key`로 결제 API 멱등성 보장 (중복 결제 방지)
- **Consumer**: `idempotencyKey`를 메시지에 포함하여 데이터 플랫폼 전송 시 중복 전송 방지
- **랭킹 Consumer**: `concertScheduleId` 기준 매진 여부를 DB에서 재조회하여 중복 랭킹 추가 방지

---

## 8. 파티셔닝·키 전략 및 운영

### 8.1 파티션 키 전략

| 토픽 | 파티션 키 | 목적 |
|------|-----------|------|
| payment-completed | reservationId | 동일 예약의 이벤트 순서 보장 |

### 8.2 토픽별 설정

| 토픽 | 파티션 수 | Replication | 향후 파티션 증가 |
|------|-----------|-------------|------------------|
| payment-completed | 3 | 1 (로컬) / 3 (운영) | 트래픽 증가 시 파티션 수만 증가 가능. 리밸런싱 시 Consumer 일시 중단 발생하므로 유지보수 시간대에 수행 권장 |

### 8.3 운영·회복 전략

- **Idempotent Producer**: `enable.idempotence=true` (중복 발행 방지)
- **Consumer-side Idempotency**: 처리 전 `idempotencyKey`로 이미 처리 여부 확인
- **DLQ 정책**: 재시도 실패 메시지는 DLQ로 격리 후 수동 처리

---

## 9. 쿠폰 발급 시나리오 (이커머스 확장 예시)

> 결제/예약/랭킹 외, Kafka를 활용할 수 있는 **쿠폰 발급** 시나리오를 설계합니다.

### 9.1 시나리오 개요

- **트리거**: 결제 완료 시 쿠폰 자동 발급 (예: 첫 결제 시 10% 할인 쿠폰)
- **Producer**: 결제 완료 이벤트와 동일한 `payment-completed` 토픽 또는 별도 `coupon-issue-request` 토픽
- **Consumer**: 쿠폰 발급 서비스가 구독하여 발급 처리

### 9.2 메시지 스키마 예시 (CouponIssueRequestMessage)

```json
{
  "requestId": "uuid",
  "userId": 100,
  "orderId": 1001,
  "couponType": "FIRST_PURCHASE_10",
  "idempotencyKey": "coupon-user100-order1001",
  "timestamp": 1709123456789
}
```

### 9.3 멱등성 (중복 발급 방지)

| 전략 | 구현 |
|------|------|
| 메시지 키 | `idempotencyKey` (userId + orderId + couponType 조합) |
| Consumer 처리 | 발급 전 DB에서 `idempotencyKey`로 이미 발급 여부 조회 |
| 중복 시 | 이미 발급된 경우 스킵 후 offset 커밋 (재처리 방지) |

### 9.4 소비자 그룹 설계

| Consumer Group | 역할 | 처리 내용 |
|----------------|------|-----------|
| coupon-issue-consumer-group | 쿠폰 발급 | DB에 쿠폰 발급 기록, 사용자 쿠폰함 업데이트 |
| coupon-statistics-consumer-group | 통계 | 발급 건수 집계, 대시보드용 |
| coupon-notification-consumer-group | 알림 | 발급 완료 푸시/이메일 발송 |

### 9.5 오류 재시도·중복 방지 전략

- **재시도**: 3회, Exponential backoff (1s, 2s, 4s)
- **DLQ**: `coupon-issue-dlq` — 재시도 실패 시 격리
- **중복 방지**: `idempotencyKey`로 발급 이력 조회 후 스킵

---

## 10. 참고

- Kafka 기초 개념: [kafka-intro.md](./kafka-intro.md)
- Docker Compose: `docker-compose.kafka.yaml`
