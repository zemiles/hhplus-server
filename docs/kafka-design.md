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

## 7. 참고

- Kafka 기초 개념: [kafka-intro.md](./kafka-intro.md)
- Docker Compose: `docker-compose.kafka.yaml`
