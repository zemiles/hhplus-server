-- ============================================
-- HHPLUS 콘서트 예약 시스템 데이터베이스 스키마
-- ============================================
-- 작성일: 2024
-- 설명: 세부 제약조건, 인덱스, 외래키를 포함한 스키마 정의
-- ============================================

-- 데이터베이스 생성 (필요시)
-- CREATE DATABASE IF NOT EXISTS hhplus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE hhplus;

-- ============================================
-- 1. 사용자 및 지갑 관련 테이블
-- ============================================

-- 사용자 테이블
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 ID',
    user_email VARCHAR(255) NOT NULL UNIQUE COMMENT '이메일 (유니크)',
    user_tel VARCHAR(20) NOT NULL UNIQUE COMMENT '전화번호 (유니크)',
    user_name VARCHAR(100) NOT NULL COMMENT '이름',
    user_status TINYINT NOT NULL DEFAULT 1 COMMENT '상태: 1=정상, 2=중지',
    wallet_id BIGINT COMMENT '지갑 ID (FK)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    modification_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    INDEX idx_user_email (user_email),
    INDEX idx_user_tel (user_tel),
    INDEX idx_user_status (user_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자';

-- 지갑 테이블
CREATE TABLE IF NOT EXISTS wallets (
    wallet_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '지갑 ID',
    user_id BIGINT NOT NULL UNIQUE COMMENT '사용자 ID (FK, 유니크)',
    balance_cents BIGINT NOT NULL DEFAULT 0 COMMENT '잔액 (센트 단위)',
    currency VARCHAR(3) NOT NULL DEFAULT 'KRW' COMMENT '통화',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    modification_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='지갑';

-- 사용자 테이블의 wallet_id 외래키 설정
ALTER TABLE users 
ADD CONSTRAINT fk_users_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id) ON DELETE SET NULL ON UPDATE CASCADE;

-- 지갑 거래 이력 테이블
CREATE TABLE IF NOT EXISTS wallet_ledger (
    ledger_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '원장 ID',
    wallet_id BIGINT NOT NULL COMMENT '지갑 ID (FK)',
    type TINYINT NOT NULL COMMENT '거래 유형: 1=충전, 2=결제, 3=환불, 4=취소, 5=조정',
    amount_cents BIGINT NOT NULL COMMENT '금액 (센트 단위)',
    balance_after_cents BIGINT NOT NULL COMMENT '거래 후 잔액 (센트 단위)',
    external_tx_id VARCHAR(255) COMMENT '외부 거래 ID',
    idempotency_key VARCHAR(255) COMMENT '멱등성 키',
    charge_date VARCHAR(8) COMMENT '거래일자 (YYYYMMDD)',
    charge_time VARCHAR(6) COMMENT '거래시간 (HHMMSS)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    modification_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_wallet_id (wallet_id),
    INDEX idx_type (type),
    INDEX idx_create_time (create_time),
    INDEX idx_idempotency_key (idempotency_key),
    UNIQUE KEY uk_wallet_idempotency (wallet_id, idempotency_key) COMMENT '멱등성 보장'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='지갑 거래 이력';

-- ============================================
-- 2. 콘서트 관련 테이블
-- ============================================

-- 콘서트 테이블
CREATE TABLE IF NOT EXISTS concerts (
    concert_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '콘서트 ID',
    concert_name VARCHAR(255) NOT NULL COMMENT '콘서트 이름',
    concert_dec TEXT COMMENT '콘서트 설명',
    concert_status TINYINT NOT NULL DEFAULT 1 COMMENT '상태: 1=종료, 2=진행중, 3=중지, 4=예약중',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    modification_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    INDEX idx_concert_status (concert_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='콘서트';

-- 콘서트 일정 테이블
CREATE TABLE IF NOT EXISTS concert_schedules (
    concert_scheduled_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '콘서트 일정 ID',
    concert_id BIGINT NOT NULL COMMENT '콘서트 ID (FK)',
    concert_date VARCHAR(8) NOT NULL COMMENT '공연일자 (YYYYMMDD)',
    concert_time VARCHAR(6) NOT NULL COMMENT '공연시간 (HHMMSS)',
    concert_price DECIMAL(10, 2) NOT NULL COMMENT '기본 가격',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    modification_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    FOREIGN KEY (concert_id) REFERENCES concerts(concert_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_concert_id (concert_id),
    INDEX idx_concert_date (concert_date),
    UNIQUE KEY uk_concert_date_time (concert_id, concert_date, concert_time) COMMENT '콘서트 일자/시간 고유성'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='콘서트 일정';

-- 좌석 테이블
CREATE TABLE IF NOT EXISTS seats (
    seat_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '좌석 ID',
    concert_schedule_id BIGINT NOT NULL COMMENT '콘서트 일정 ID (FK)',
    seat_number INT NOT NULL COMMENT '좌석 번호',
    seat_grade TINYINT NOT NULL COMMENT '좌석 등급: 1=VIP, 2=R석, 3=S석, 4=A석',
    seat_status TINYINT NOT NULL DEFAULT 2 COMMENT '상태: 1=예약중, 2=예약하지않음',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    modification_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    FOREIGN KEY (concert_schedule_id) REFERENCES concert_schedules(concert_scheduled_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_schedule_id (concert_schedule_id),
    INDEX idx_seat_status (seat_status),
    INDEX idx_schedule_status (concert_schedule_id, seat_status) COMMENT '일정별 좌석 상태 조회 최적화',
    UNIQUE KEY uk_schedule_seat_number (concert_schedule_id, seat_number) COMMENT '일정별 좌석 번호 고유성'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='좌석';

-- ============================================
-- 3. 예약 관련 테이블
-- ============================================

-- 예약 테이블
CREATE TABLE IF NOT EXISTS reservations (
    reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '예약 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID (FK)',
    concert_schedule_id BIGINT NOT NULL COMMENT '콘서트 일정 ID (FK)',
    seat_id BIGINT NOT NULL COMMENT '좌석 ID (FK)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '상태: 1=PENDING, 2=HOLD, 3=EXPIRED, 4=CANCELLED, 5=PAID',
    hold_expires_at DATETIME COMMENT '홀드 만료 시각',
    amount_cents BIGINT NOT NULL COMMENT '예약 금액 (센트 단위)',
    idempotency_key VARCHAR(255) COMMENT '멱등성 키',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    modification_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (concert_schedule_id) REFERENCES concert_schedules(concert_scheduled_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (seat_id) REFERENCES seats(seat_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_schedule_id (concert_schedule_id),
    INDEX idx_seat_id (seat_id),
    INDEX idx_status (status),
    INDEX idx_hold_expires_at (hold_expires_at) COMMENT '만료된 홀드 조회 최적화',
    INDEX idx_user_reservation (user_id, reservation_id) COMMENT '사용자별 예약 이력 조회',
    INDEX idx_schedule_reservation (concert_schedule_id, reservation_id) COMMENT '일정별 예약 이력 조회',
    UNIQUE KEY uk_seat_id_active (seat_id, status) COMMENT '좌석별 활성 예약 유니크 (MySQL은 부분 유니크 미지원, 애플리케이션 레벨 처리 필요)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='예약';

-- ============================================
-- 4. 결제 관련 테이블
-- ============================================

-- 결제 메인 테이블
CREATE TABLE IF NOT EXISTS payments_main (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '결제 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID (FK)',
    payment_type TINYINT NOT NULL COMMENT '결제 유형',
    total_amount_cents BIGINT NOT NULL COMMENT '총 결제 금액 (센트 단위)',
    currency VARCHAR(3) NOT NULL DEFAULT 'KRW' COMMENT '통화',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '상태: 1=INIT, 2=APPROVED, 3=FAILED, 4=CANCELLED, 5=PARTIAL',
    provider VARCHAR(50) COMMENT '결제 제공자',
    provider_tx_id VARCHAR(255) COMMENT '결제 제공자 거래 ID',
    approved_at DATETIME COMMENT '승인 시각',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE COMMENT '멱등성 키 (유니크)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    modification_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_idempotency_key (idempotency_key),
    INDEX idx_user_payment (user_id, payment_id) COMMENT '사용자별 결제 이력 조회',
    INDEX idx_provider_tx_id (provider_tx_id) COMMENT '결제 제공자 거래 ID 조회'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='결제 메인';

-- 결제 상세 테이블
CREATE TABLE IF NOT EXISTS payments_detail (
    payment_detail_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '결제 상세 ID',
    payment_id BIGINT NOT NULL COMMENT '결제 ID (FK)',
    reservation_id BIGINT COMMENT '예약 ID (FK, nullable)',
    ledger_id BIGINT COMMENT '원장 ID (FK, nullable)',
    amount_cents BIGINT NOT NULL COMMENT '금액 (센트 단위)',
    description VARCHAR(255) COMMENT '설명',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    modification_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    FOREIGN KEY (payment_id) REFERENCES payments_main(payment_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id) ON DELETE SET NULL ON UPDATE CASCADE,
    FOREIGN KEY (ledger_id) REFERENCES wallet_ledger(ledger_id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_payment_id (payment_id),
    INDEX idx_reservation_id (reservation_id),
    INDEX idx_ledger_id (ledger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='결제 상세';

-- ============================================
-- 5. 대기열 관련 테이블 (선택)
-- ============================================

-- 대기열 토큰 테이블
CREATE TABLE IF NOT EXISTS queue_tokens (
    token_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '토큰 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID (FK)',
    concert_schedule_id BIGINT NOT NULL COMMENT '콘서트 일정 ID (FK)',
    token VARCHAR(255) NOT NULL UNIQUE COMMENT '토큰 (UUID, 유니크)',
    position INT NOT NULL COMMENT '대기 순번',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '상태: 1=WAITING, 2=READY, 3=ENTERED, 4=EXPIRED, 5=BLOCKED',
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 시각',
    ready_until DATETIME COMMENT '준비 만료 시각',
    last_poll_at DATETIME COMMENT '마지막 폴링 시각',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    modification_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (concert_schedule_id) REFERENCES concert_schedules(concert_scheduled_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_schedule_id (concert_schedule_id),
    INDEX idx_token (token),
    INDEX idx_schedule_status_position (concert_schedule_id, status, position) COMMENT '일정별 상태/순번 조회 최적화'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='대기열 토큰';

-- ============================================
-- 인덱스 및 제약조건 요약
-- ============================================
-- 
-- 주요 유니크 제약조건:
-- - users.user_email
-- - users.user_tel
-- - wallets.user_id
-- - wallet_ledger(wallet_id, idempotency_key) - 멱등성
-- - concert_schedules(concert_id, concert_date, concert_time)
-- - seats(concert_schedule_id, seat_number)
-- - reservations(seat_id, status) - 부분 유니크 (MySQL 제한으로 인덱스만)
-- - payments_main.idempotency_key
-- - queue_tokens.token
--
-- 주요 인덱스:
-- - 조회 성능: schedule_status, user_reservation, schedule_reservation 등
-- - 검색 성능: user_email, user_tel, provider_tx_id 등
-- - 만료 처리: hold_expires_at, ready_until 등
-- ============================================

