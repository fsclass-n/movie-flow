import os
from dotenv import load_dotenv

# 현재 파일 위치 기준 .env 로드
load_dotenv()

# .env의 키값과 동일하게 수정 (Render Environment Variables 설정 필수)
EMAIL_ADDRESS = os.getenv("MAIL_USER")
EMAIL_PASSWORD = os.getenv("MAIL_PASS")  # 구글 앱 비밀번호 16자리

# 클라우드 환경(Render) 안정성을 위해 SSL/465 고정 사용 권장
SMTP_HOST = os.getenv("MAIL_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("MAIL_PORT", 465))