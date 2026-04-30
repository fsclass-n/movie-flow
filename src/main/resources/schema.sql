-- 기존 테이블 삭제 (필요 시)
DROP TABLE IF EXISTS alerts;
DROP TABLE IF EXISTS rpa_logs;
DROP TABLE IF EXISTS movies;
DROP TABLE IF EXISTS users;

-- 1. 영화 정보 테이블 (available_seats 추가됨)
CREATE TABLE IF NOT EXISTS movies ( 
    id BIGINT AUTO_INCREMENT PRIMARY KEY, 
    title VARCHAR(200) NOT NULL, 
    theater_name VARCHAR(100) NOT NULL, 
    start_time VARCHAR(50) NOT NULL, 
    total_seats INT DEFAULT 0, 
    good_seats INT DEFAULT 0, 
    description TEXT,
    image_url VARCHAR(255),
    available_seats TEXT, -- 상세 좌석 정보용 컬럼 필수 추가
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. 사용자 테이블
CREATE TABLE IF NOT EXISTS users ( 
    id BIGINT AUTO_INCREMENT PRIMARY KEY, 
    email VARCHAR(100) NOT NULL UNIQUE, 
    username VARCHAR(255),
    password VARCHAR(255) NOT NULL, 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 알림 신청 테이블
CREATE TABLE IF NOT EXISTS alerts ( 
    id BIGINT AUTO_INCREMENT PRIMARY KEY, 
    user_id BIGINT, 
    movie_id BIGINT, 
    email VARCHAR(255), -- 알림용 이메일 중복 저장 (성능 최적화)
    status VARCHAR(20) DEFAULT 'WATCHING', 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, 
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);

-- 4. 관리자용 로그 테이블
CREATE TABLE IF NOT EXISTS rpa_logs ( 
    id BIGINT AUTO_INCREMENT PRIMARY KEY, 
    log_level VARCHAR(10) NOT NULL, 
    message TEXT NOT NULL, 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);