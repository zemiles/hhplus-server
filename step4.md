- **콘서트 예약 서비스**

  **1. Infrastructure Layer 구현**

    - ReservationTokenRepository, SeatReservationRepository, UserBalanceRepository, PaymentRepository 등 구현
    - 임시 좌석 배정: **Redis 없이도 상태 컬럼 + 만료 시간 방식으로 구현 가능**
    - 대기열 토큰 관리: Redis 또는 DB 기반으로 구현

  **2. 기능별 통합 테스트 작성**

    - 유저가 토큰을 발급받고 → 좌석 예약 요청 → 결제 완료까지의 흐름 테스트
    - 만료 시간 도래 후 좌석이 다시 예약 가능한지 확인
    - 다중 유저가 동시에 좌석 요청 시 한 명만 성공하도록 테스트 구성

> `Infrastructure` 는 RDMBS ( MySQL ) 기반으로 작성합니다.
>

### **`(선택)심화 과제 - DB`**

- 조회가 오래 걸릴 수 있는 기능을 리스트업하고 분석하여, 테이블 재설계 / 인덱스 등 솔루션을 도출하는 내용의 보고서 작성
- 주요 기능별 동시성 테스트 작성

> 이번 과제에서 동시성 테스트는 성공하는 것이 목적이 아니라, 어떤 기능에 대해 동시성 이슈가 예민할지를 미리 리스트업하고 작성하여 Rule 로 가두는 것을 목적으로 합니다.
>