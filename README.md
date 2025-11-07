## 프로젝트

## Getting Started

### Prerequisites

#### Running Docker Containers

`local` profile 로 실행하기 위하여 인프라가 설정되어 있는 Docker 컨테이너를 실행해주셔야 합니다.

```bash
docker-compose up -d
```
    🎫 콘서트 예약 서비스 — 아키텍처 개요 & ADR 요약
    📦 Project

    서버 사이드 예약/결제/대기열 시스템.
    프론트엔드/운영 포털/정산 백오피스는 본 문서 범위 밖입니다(필요 시 후속 ADR).

    🚀 Getting Started
    Prerequisites

    Docker / Docker Compose

    JDK 17+ (애플리케이션 빌드 시)

    kubectl / Helm (배포 자동화 사용 시 선택)

    Running Docker Containers (local profile)

    로컬에서 local 프로파일로 실행하기 위해 인프라 컨테이너를 먼저 띄웁니다.

    docker-compose up -d


    애플리케이션은 local 프로파일로 실행합니다.

    # 예시
        SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
    # 또는
        java -jar -Dspring.profiles.active=local app.jar

    🎯 목적/범위

    이 문서는 서버 사이드 예약/결제/대기열 시스템의 아키텍처 개요와
    핵심 의사결정(ADR) 을 요약합니다.

    비범위: 프론트엔드 UI, 내부 백오피스, 정산 배치(별도 문서/ADR로 관리).

    🔒 비기능 요구사항(NFR)

    성능/확장성: 피크 트래픽(예: 수만 QPS) 흡수

    정합성: 좌석 중복 방지, 결제 멱등성

    가용성: 멀티-AZ, 자동 복구

    관측성: 로그/메트릭/APM/트레이싱

    보안: HTTPS/WAF/비밀 관리(Secrets)

    🧭 시스템 개요(1-page)

    요청 흐름: LB → API → Redis(토큰/락/캐시) → MySQL(트랜잭션)
    (선택) → MQ → Worker

    대기열: Redis ZSET/SET + 토큰(HASH, TTL), 초당 N명 승격(Worker)

    좌석 홀드: Redis 분산락(SET NX PX) → MySQL 트랜잭션(HOLD/만료시각)

    결제/멱등: idempotency_key 유니크, 승인 성공 시 PAID + 좌석 낙관적 UPDATE

    비동기 처리: 초기엔 Transactional Outbox(DB 폴링) → 규모 커지면 MQ 도입

    다이어그램은 docs/infra.md 혹은 리포 내 Mermaid 파일을 참고하세요.

    📌 ADR 요약(결정 · 근거 · 대안)
    1) DB = MySQL(InnoDB)

    결정: MySQL 채택

    근거: 팀 역량/운영 친숙도, 트랜잭션/정합성 확보

    대안: Postgres(기능 풍부) — 팀 스택/운영 고려로 보류

    2) 캐시/락/대기열 = Redis

            결정: Redis로 토큰 TTL/분산락/저지연 캐시 처리
            
            근거: 스파이크 흡수, 단순한 락/TTL 만료 모델
            
    대안: DB 락만 사용 — 경합/성능 리스크 큼
    
    3) 비동기 = Outbox 우선, MQ 선택
    
    결정: 초기 Transactional Outbox(동일 트랜잭션), 필요 시 MQ로 확장
    
    근거: 러닝커브↓, 데이터 일관성↑
    
    대안: SQS/Rabbit/Kafka 즉시 도입 — 초기 복잡도↑
    
    4) 배포 = Kubernetes Canary
    
    결정: 5%→20%→50% 단계, 실패 시 즉시 롤백
    
    근거: 피크 이벤트 시 안전 릴리스
    
    대안: 단순 롤링 — 리스크 관리 한계
    
    5) 격리수준/락 전략
    
    결정: MySQL REPEATABLE READ(기본), 경합 높으면 READ COMMITTED 검토
    
    근거: 좌석 경합 + 결제 트랜잭션 특성
    
    대안: 전역 직렬화 — 대기/락 비용 과다
    
    6) 멱등성
    
    결정: payments_main(idempotency_key UNIQUE) / reservations(idempotency_key)
    
    근거: 재시도/중복 클릭/PG 재전송 대응
    
    대안: 애플리케이션 캐시만 — 신뢰성 낮음
    
    🧪 운영/확장 전략
    
    오토스케일(HPA): p95 지연/CPU/큐 길이(도입 시) 기반 스케일
    
    읽기 분리: MySQL Read Replica로 조회 분산
    
    캐시 무효화: 확정/취소/만료 시 seatmap:{scheduleId} 삭제
    
    KPI/알람: 대기열 길이·승격률·평균 대기·홀드 만료율·결제 실패율·멱등 충돌률
    
    보안/네트워크: WAF/ALB, SG 최소 개방, Secrets Manager/SSM
    
    🗂️ 문서 링크(예시)
    
    docs/infra.md — 인프라 구성도(High-level/배포/보안/운영 요약)
    
    docs/adr/adr-0001-db-cache-queue.md — DB/Redis/Outbox 결정 상세
    
    docs/adr/adr-0002-deploy-canary.md — 배포 전략/롤백 절차
    
    docs/runbook/ops.md — 장애 대응/알람 기준/KPI 대시보드