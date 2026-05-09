SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM alerts;
DELETE FROM movies;
DELETE FROM rpa_logs;
SET FOREIGN_KEY_CHECKS = 1;

INSERT IGNORE INTO users (id, username, password, email)
VALUES (1, 'admin', '1234', 'admin@movieflow.com');

INSERT INTO movies (title, theater_name, start_time, total_seats, good_seats, description, image_url, available_seats) VALUES
('인사이드 아웃 2', 'CGV 강남', '2026-05-01 14:30', 150, 3, '낯선 기억과 감정을 따라가는 모험', '/images/inside_out_2.jpg', '[["C",7],["D",8],["E",10]]'),
('퓨리오사: 매드맥스 사가', '롯데시네마 건대', '2026-05-01 15:00', 175, 2, '고향으로 돌아가기 위한 퓨리오사의 여정', '/images/furiosa.jpg', '[["C",5],["D",8],["F",20]]'),
('원더랜드', '메가박스 코엑스', '2026-05-01 18:20', 150, 1, '다시 만나고 싶은 사람을 마주하는 이야기', '/images/wonderland.jpg', '[["E",6],["F",5],["G",6]]');

INSERT INTO rpa_logs (log_level, message, created_at) VALUES
('INFO', '시스템이 정상적으로 시작되었습니다.', CURRENT_TIMESTAMP),
('INFO', 'CGV 강남 영화 정보를 업데이트했습니다.', CURRENT_TIMESTAMP),
('WARN', '메가박스 코엑스 사이트 응답이 지연되고 있습니다.', CURRENT_TIMESTAMP);
