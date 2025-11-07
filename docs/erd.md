## ✅ 체크리스트

- [ ] 도메인 모델과 테이블 구조 일치(대기열 · 사용자 · 카탈로그 · 예약 · 결제 · 지갑)
- [ ] FK / 제약 / 인덱스 / 유니크키로 무결성·성능 확보
- [ ] 트랜잭션 경계, 격리수준, 락 전략 정의(행 잠금 / 낙관적 버전)
- [ ] 실패 시 롤백 및 보상 흐름 마련

---

## 🔎 모델링 포인트 (요약)

- 좌석의 **영구 상태 최소화**: `AVAILABLE` / `RESERVED`
- **홀드(임시 점유)** 는 `RESERVATION` + `hold_expires_at` 로 표현
- 좌석은 시간축으로 **여러 예약 이력(1:N)** 가능
- **활성 예약만 중복 금지**: `(schedule_id, seat_number)` 고유 보장
- **멱등성 보장**: `payments_main(idempotency_key)` / `wallet_ledger(idempotency_key)` 유니크

---

## 📊 시스템 개요

| 항목 | 값 |
|---|---|
| 엔터티 | 9개: `users`, `wallets`, `wallet_ledger`, `concerts`, `concert_schedules`, `seats`, `reservations`, `payments_main`, `payments_detail` (+ `queue_tokens` 선택) |
| 도메인 | 대기열, 사용자, 카탈로그(회차/좌석), 예약, 결제, 지갑 |
| 핵심 제약 | 좌석 중복 방지, 멱등 결제, 홀드 TTL 만료, 부분취소 대응 |
| 동시성 | Redis 락(SETNX+TTL) + DB 트랜잭션(SELECT … FOR UPDATE) + 낙관적 버전(선택) |
| 확장 | 멀티 AZ, 오토스케일, 배치 승격(초당 처리량 조정) |

---

## 🗺️ ERD (Mermaid)

## 🧱 엔터티 개요 (요약 카드)

### USERS
- 키: `user_id(PK)`
- 핵심: `email(UQ)`, `phone(UQ)`, `name`, `status`
- 관계: 1–N `reservations`, 1–N `payments_main`, 1–1 `wallets`, 1–N `queue_tokens`(선택)

### WALLETS
- 키: `wallet_id(PK)` / `user_id(FK)`
- 핵심: `user_code(UQ)`, `currency`, `balance_cents`
- 관계: 1–N `wallet_ledger`

### WALLET_LEDGER
- 키: `ledger_id(PK)` / `wallet_id(FK)`
- 핵심: `type`, `amount_cents`, `balance_after_cents`, `external_tx_id`
- 멱등(선택): `UNIQUE(wallet_id, idempotency_key)`

### CONCERTS
- 키: `concert_id(PK)`
- 핵심: `name`, `description`, `status`
- 관계: 1–N `concert_schedules`

### CONCERT_SCHEDULES
- 키: `schedule_id(PK)` / `concert_id(FK)`
- 핵심: `starts_at`, `base_price_cents`, `sales_status`
- 제약: `UNIQUE(concert_id, starts_at)`
- 관계: 1–N `seats`, 1–N `reservations`

### SEATS
- 키: `seat_id(PK)` / `schedule_id(FK)`
- 핵심: `seat_number`, `grade`, `price_cents`, `status`
- 제약: `UNIQUE(schedule_id, seat_number)`
- 인덱스: `(schedule_id, status)`
- 관계: 1–N `reservations`(시간 이력)

### RESERVATIONS
- 키: `reservation_id(PK)` / `user_id(FK)` / `schedule_id(FK)` / `seat_id(FK)`
- 핵심: `status(PENDING|HOLD|EXPIRED|CANCELLED|PAID)`, `hold_expires_at`, `amount_cents`, `idempotency_key`
- 제약(일반): `UNIQUE(seat_id)` (동일 시점 점유 방지)
- 제약(선호): *부분 유니크 가능 시* `UNIQUE(schedule_id, seat_number) WHERE status IN ('HOLD','PAID','CONFIRMED')`

### PAYMENTS_MAIN
- 키: `payment_id(PK)` / `user_id(FK)`
- 핵심: `payment_type`, `total_amount_cents`, `currency`, `status`, `provider`, `provider_tx_id`, `approved_at`, `idempotency_key(UQ)`
- 관계: 1–N `payments_detail`

### PAYMENTS_DETAIL
- 키: `payment_detail_id(PK)` / `payment_id(FK)`
- 라인: `reservation_id(FK, nullable)`, `ledger_id(FK, nullable)`, `amount_cents`, `description`
- 비고: **혼합결제**(좌석+지갑 차감) 라인 분할 지원

### QUEUE_TOKENS (선택)
- 키: `token_id(PK)` / `user_id(FK)` / `schedule_id(FK)`
- 핵심: `token(UQ)`, `position`, `status`, `issued_at`, `ready_until`, `last_poll_at`

---

## 🧩 관계 요약 (텍스트)
- Concerts 1–N Schedules, Schedules 1–N Seats, Schedules 1–N Reservations
- Users 1–N Reservations / 1–N Payments / 1–N QueueTokens, Users 1–1 Wallets
- Wallets 1–N WalletLedger
- PaymentsMain 1–N PaymentsDetail
- 좌석의 점유는 **Reservations**로만 표현(Seats는 영구 상태만 유지)

---

## 🔧 인덱스 & 제약 (요약표)

| 영역 | 인덱스/제약 | 목적 |
|-----|-------------|------|
| Seats | `UNIQUE(schedule_id, seat_number)` | 회차 내 좌석 고유성 보장 |
| Seats | `(schedule_id, status)` | 가용/점유 좌석 필터 최적화 |
| Reservations | `UNIQUE(seat_id)` *(또는 부분 유니크)* | 동시 점유 방지 |
| Reservations | `(user_id, reservation_id)`, `(schedule_id, reservation_id)` | 사용자/회차별 이력 조회 |
| PaymentsMain | `UNIQUE(idempotency_key)` | 멱등 결제 보장 |
| PaymentsMain | `(user_id, payment_id)` | 사용자 결제 이력 조회 |
| PaymentsDetail | `(payment_id)` | 라인 합산/정산 |
| WalletLedger | `(wallet_id)` | 잔액 재구성·이력 조회 |
| QueueTokens | `(schedule_id, status, position)` | 공정한 승격·페이지네이션 |

> 부분 유니크(PostgreSQL 예):  
> `CREATE UNIQUE INDEX ux_resv_active ON reservations(schedule_id, seat_id) WHERE status IN ('HOLD','PAID','CONFIRMED');`

---

## 🔒 트랜잭션 & 동시성 (운영 가이드)
- **예약 생성**: `BEGIN` → 좌석 키/행 잠금 → `RESERVATION(HOLD)` 삽입 → `COMMIT`
- **홀드 만료**: 워커가 `hold_expires_at` 경과분을 `EXPIRED`로 전환
- **결제 승인**: 멱등키 확인 → 승인 → 예약 `PAID` 확정 → 좌석 `RESERVED` 반영
- 격리수준: 기본 `READ COMMITTED`(MySQL/InnoDB, Postgres), 필요 구간 `REPEATABLE READ`
- 락 전략: **행 잠금**(단기) + **낙관적 버전**(충돌 흔한 테이블)

---

## 🧯 롤백/보상 플로우
- PG 승인 실패 → 결제/원장/예약 상태 역전
- 지갑 차감 후 실패 → `wallet_ledger`에 `REFUND` 라인 추가(보상)
- 예약 확정 실패 → 예약 `CANCELLED` + 좌석 상태 복구

---

## 🧾 Enum 스케치
- `SeatStatus`: `AVAILABLE`, `RESERVED`
- `ReservationStatus`: `PENDING`, `HOLD`, `EXPIRED`, `CANCELLED`, `PAID`
- `PaymentStatus`: `INIT`, `APPROVED`, `FAILED`, `CANCELLED`, `PARTIAL`
- `LedgerType`: `CHARGE`, `PAYMENT`, `REFUND`, `CANCEL`, `ADJUST`
- `QueueStatus`: `WAITING`, `READY`, `ENTERED`, `EXPIRED`, `BLOCKED`

---
