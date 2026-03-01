# 이벤트 기반 부가 로직 및 보상 전략

## 1. 개요

결제 완료 후 랭킹 업데이트, 데이터 플랫폼 전송 등 부가 로직은 **트랜잭션 커밋 이후** 이벤트로 발행되어 처리됩니다.

## 2. 트랜잭션 커밋 후 이벤트 발행

### 2.1 구현 방식

- `TransactionSynchronizationManager.isSynchronizationActive()`: 트랜잭션 경계 내부에서만 동기화 등록
- `PaymentCompletedEventSynchronization`: `TransactionSynchronization` 인터페이스 명시적 구현
- `afterCommit()`: 커밋 완료 후에만 이벤트 발행
- 이벤트 source: `paymentId` (디버깅 시 추적 용이)

### 2.2 흐름

```
[ProcessPaymentUseCase]
  → 트랜잭션 내: 결제 저장, Ledger 기록, 예약 상태 업데이트
  → isSynchronizationActive() 체크 후 TransactionSynchronization 등록
  → 트랜잭션 커밋
  → afterCommit() → PaymentCompletedEvent 발행
```

## 3. 외부 전송 실패 처리 (보상/재시도)

### 3.1 현재 전략

| 부가 로직 | 실패 시 동작 | 비고 |
|-----------|--------------|------|
| 랭킹 업데이트 | 로그만 기록, 예외 비전파 | DB 기반이므로 재시도 시 자동 복구 |
| 데이터 플랫폼 전송 | 로그만 기록, 예외 비전파 | 외부 API 실패 시 데이터 유실 가능 |

### 3.2 권장 보상 전략

**데이터 플랫폼 전송 (중요 데이터):**

1. **재시도**: 최대 3회, Exponential backoff (1s, 2s, 4s)
2. **재전송 큐**: 실패 시 별도 토픽/테이블에 적재 → 배치로 재전송
3. **상태 마킹**: `event_outbox` 테이블에 실패 건 기록 → 수동 확인

**랭킹 업데이트:**

- DB 기반 조회이므로 재시도 시 일관성 유지
- 멱등성: `addSoldOutConcert`는 동일 concertScheduleId 중복 추가 시에도 안전

## 4. 리스너 멱등성 (Idempotency)

### 4.1 필요성

- 이벤트 중복 발행 (네트워크 재시도 등)
- Kafka Consumer 재시도
- 동일 결제에 대한 중복 처리 방지

### 4.2 구현

- `EventIdempotencyPort`: `tryAcquireProcessing(idempotencyKey, processorType)`
- Redis `SET key NX EX`: 동일 키 중복 처리 방지, TTL 24시간
- 적용 대상: 랭킹 리스너, 데이터 플랫폼 리스너 (Spring Event + Kafka Consumer)

## 5. TaskExecutor 운영 설정

| 항목 | 현재값 | 운영 권장 |
|------|--------|-----------|
| corePoolSize | 5 | 트래픽에 따라 10~20 |
| maxPoolSize | 10 | 20~50 |
| queueCapacity | 100 | 500~1000 |

- 실패 이벤트 카운트, 재시도율을 지표로 수집
- 임계치 초과 시 알림 설정

## 6. 참고 키워드

- TransactionSynchronization
- Idempotency (멱등성)
- Compensation (보상) 전략
- 이벤트 기반 예외/재시도 정책
