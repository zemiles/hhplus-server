# Kafka 기초 개념 - 동료 공유 문서

> Kafka를 잘 모르는 동료들을 위한 입문 문서입니다.

---

## 1. 이벤트 아이디어의 시스템 전체 관점 확장

### 1.1 기존 이벤트 처리의 한계

우리가 사용하는 **Spring Application Event**는 **단일 JVM 내에서** 동작합니다.

```
[결제 서비스]  →  publishEvent()  →  [EventListener들] (같은 프로세스)
```

- **한계**: 마이크로서비스, 여러 서버, 다른 언어/시스템과 이벤트를 공유하기 어렵습니다.
- **한계**: 서버가 재시작되면 처리 중이던 이벤트가 사라질 수 있습니다.

### 1.2 이벤트를 "시스템 전체"로 확장한다는 것

**메시지 브로커(Message Broker)**를 두고, 여러 시스템이 **동일한 이벤트 스트림**을 공유합니다.

```
[결제 서비스]     →   [메시지 브로커]  →  [랭킹 서비스]
[예약 서비스]     →      (Kafka)      →  [데이터 플랫폼]
[주문 서비스]     →                   →  [알림 서비스]
                                        [분석 서비스]
```

- **발행자(Producer)**: 이벤트를 브로커에 전송
- **구독자(Consumer)**: 브로커에서 이벤트를 읽어 처리
- **결과**: 서로 다른 서비스, 서버, 언어가 같은 이벤트를 기반으로 협업 가능

---

## 2. Kafka 사용 시 장단점

### 2.1 장점

| 장점 | 설명 |
|------|------|
| **고성능** | 초당 수백만 건 처리 가능. 디스크 순차 쓰기로 빠름. |
| **확장성** | 파티션 단위로 수평 확장, 브로커 추가로 처리량 증가. |
| **영속성** | 메시지를 디스크에 저장. Consumer가 다운돼도 메시지 유실 없음. |
| **재처리 가능** | Consumer가 offset을 관리. 원하는 시점부터 다시 읽을 수 있음. |
| **여러 Consumer** | 동일 토픽을 여러 Consumer 그룹이 각각 독립적으로 소비. |
| **느슨한 결합** | Producer와 Consumer가 서로를 모르고 메시지로만 통신. |

### 2.2 단점

| 단점 | 설명 |
|------|------|
| **운영 복잡도** | Zookeeper/KRaft, 브로커, 토픽, 파티션 등 관리 포인트가 많음. |
| **학습 곡선** | 개념(토픽, 파티션, offset, consumer group 등) 이해 필요. |
| **적합하지 않은 상황** | 실시간 요청-응답, 매우 작은 트래픽에는 과할 수 있음. |
| **인프라 비용** | 별도 클러스터 구축/운영 또는 Managed 서비스 비용 발생. |

---

## 3. Kafka 주요 요소

### 3.1 아키텍처 개요

```
                    ┌─────────────────────────────────────────┐
                    │              Kafka Cluster               │
Producer  ────────► │  ┌─────────┐  ┌─────────┐  ┌─────────┐ │  ────────►  Consumer
                    │  │ Broker1 │  │ Broker2 │  │ Broker3 │ │
                    │  └─────────┘  └─────────┘  └─────────┘ │
                    │        ▲            ▲            ▲       │
                    │        └────────────┴────────────┘       │
                    │              Zookeeper/KRaft              │
                    └─────────────────────────────────────────┘
```

### 3.2 핵심 용어

| 용어 | 설명 |
|------|------|
| **Broker** | Kafka 서버. 메시지를 저장하고 제공. |
| **Topic** | 메시지가 저장되는 논리적 카테고리(예: `payment-completed`, `reservation-events`). |
| **Partition** | 토픽을 나눈 물리적 단위. 병렬 처리와 확장성의 기본 단위. |
| **Producer** | 토픽에 메시지를 발행하는 클라이언트. |
| **Consumer** | 토픽에서 메시지를 읽는 클라이언트. |
| **Consumer Group** | 여러 Consumer가 그룹으로 묶여, 파티션을 나눠 소비. |
| **Offset** | 파티션 내 각 메시지의 순번. Consumer가 어디까지 읽었는지 기록. |
| **Replication** | 파티션을 여러 브로커에 복제하여 가용성 확보. |

### 3.3 Topic과 Partition

```
Topic: payment-completed
├── Partition 0: [msg0, msg3, msg6, ...]
├── Partition 1: [msg1, msg4, msg7, ...]
└── Partition 2: [msg2, msg5, msg8, ...]
```

- 메시지는 **파티션 키(Key)**에 따라 특정 파티션에 배치됨.
- 같은 키를 가진 메시지는 같은 파티션 → **순서 보장**.
- 파티션 수만큼 **병렬 처리** 가능.

---

## 4. Kafka 핵심 기능

### 4.1 메시지 영속성과 재생

- 메시지는 디스크에 **로그(로그) 형태**로 저장.
- **보존 기간(retention)** 동안 유지 (예: 7일, 1GB 등).
- Consumer는 **offset**으로 읽은 위치를 관리하여, 이전 데이터를 다시 읽을 수 있음.

### 4.2 Consumer Group

```
Topic: payment-completed (3 partitions)
Consumer Group: data-platform
├── Consumer A → Partition 0
├── Consumer B → Partition 1
└── Consumer C → Partition 2
```

- 하나의 파티션은 **그룹 내 하나의 Consumer**만 담당.
- Consumer 추가/제거 시 **리밸런싱**으로 파티션 재할당.

### 4.3 Exactly-Once Semantics (EOS)

- Producer: 멱등성 + 트랜잭션으로 **중복 발행 방지**.
- Consumer: **트랜잭션 기반** 읽기-처리-커밋으로 exactly-once 처리 가능.

---

## 5. 우리 프로젝트에서의 활용

### 5.1 현재 구조 (Spring Event)

- `PaymentCompletedEvent` → `PaymentRankingEventListener`, `PaymentDataPlatformEventListener`
- 같은 JVM, 비동기 처리만 `@Async`로 수행.

### 5.2 Kafka 도입 후

- `PaymentCompletedEvent` → **Kafka Topic `payment-completed`**에 발행.
- **랭킹 서비스**: `payment-completed` 구독 → 매진 시 랭킹 반영.
- **데이터 플랫폼**: `payment-completed` 구독 → 예약/결제 데이터 수집.
- 결제 서비스가 다운돼도, Kafka에 쌓인 메시지는 유지됨.

---

## 6. 참고 자료

- [Apache Kafka 공식 문서](https://kafka.apache.org/documentation/)
- [Confluent 문서](https://docs.confluent.io/)

---

*작성일: 2025년 2월*
