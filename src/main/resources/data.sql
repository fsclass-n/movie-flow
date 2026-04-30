-- 기존 데이터 삭제 (중복 방지)
SET FOREIGN_KEY_CHECKS = 0; -- 외래 키 체크 끄기
TRUNCATE TABLE alerts;
TRUNCATE TABLE movies;
SET FOREIGN_KEY_CHECKS = 1;

-- 1. 사용자 먼저 추가
INSERT IGNORE INTO users (id, username, password, email) VALUES (1, 'hong', '1234', 'hong@example.com');
-- 2. 영화 및 상영관 정보 삽입
INSERT INTO movies (title, theater_name, start_time, good_seats, description, image_url) VALUES
('인사이드 아웃 2', 'CGV 강남', '14:30 상영', 8, '나쁜 기억은 잊어, 기쁨이와 함께하는 감정 모험', '/images/inside_out_2.jpg'),
('퓨리오사: 매드맥스 사가', '롯데시네마 건대', '15:00 상영', 3, '고향으로 돌아가기 위한 퓨리오사의 거대한 여정', '/images/furiosa.jpg'),
('원더랜드', '메가박스 코엑스', '18:20 상영', 3, '죽은 사람을 다시 만나는 가상 세계 서비스', '/images/wonderland.jpg');
-- 3. (선택사항) 알림 신청 테스트용 데이터
-- users 테이블에 id가 1인 사용자가 있다고 가정합니다.
INSERT INTO alerts (user_id, movie_id, email, status) VALUES (1, 1, 'test@example.com', 'WATCHING');