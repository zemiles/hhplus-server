# 데이터베이스 스키마 스크립트

## 파일 설명

- `schema.sql`: 전체 데이터베이스 스키마 생성 스크립트 (제약조건, 인덱스 포함)

## 사용 방법

### 방법 1: 수동 실행 (권장)

MySQL 컨테이너에 직접 접속하여 스크립트 실행:

```bash
# 스크립트 파일을 컨테이너에 복사하고 실행
docker cp src/main/resources/sql/schema.sql server-java-mysql-1:/tmp/schema.sql
docker exec -i server-java-mysql-1 mysql -uapplication -papplication hhplus < src/main/resources/sql/schema.sql

# 또는 컨테이너 내부에서 실행
docker exec -it server-java-mysql-1 mysql -uapplication -papplication hhplus
# MySQL 프롬프트에서:
source /tmp/schema.sql;
```

### 방법 2: Spring Boot 자동 실행

`application.yml`에서 다음 설정 활성화:

```yaml
spring:
  datasource:
    initialization-mode: always
    schema: classpath:sql/schema.sql
```

주의: `ddl-auto: update`와 함께 사용 시 충돌 가능성이 있습니다.

### 방법 3: MySQL 클라이언트에서 직접 실행

```bash
# 로컬 MySQL 클라이언트 사용
mysql -h localhost -P 3306 -u application -papplication hhplus < src/main/resources/sql/schema.sql
```

## 스키마 특징

### 제약조건
- 외래키 (Foreign Keys)
- 유니크 제약조건 (Unique Constraints)
- NOT NULL 제약조건

### 인덱스
- 조회 성능 최적화 인덱스
- 검색 성능 인덱스
- 복합 인덱스

### 테이블 구조
- 사용자 및 지갑: users, wallets, wallet_ledger
- 콘서트: concerts, concert_schedules, seats
- 예약: reservations
- 결제: payments_main, payments_detail
- 대기열: queue_tokens

## 주의사항

1. **기존 데이터**: 스크립트 실행 전 기존 데이터 백업 권장
2. **부분 유니크**: MySQL은 부분 유니크를 지원하지 않으므로, `reservations` 테이블의 활성 예약 유니크는 애플리케이션 레벨에서 처리 필요
3. **순서**: 외래키 의존성을 고려하여 테이블 생성 순서가 중요합니다

