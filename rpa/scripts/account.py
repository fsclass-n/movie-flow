# account.py
import os
import smtplib
from dotenv import load_dotenv

# 현재 파일 위치 기준 .env 로드
load_dotenv()

# .env의 키값과 동일하게 수정
EMAIL_ADDRESS = os.getenv("MAIL_USER")
EMAIL_PASSWORD = os.getenv("MAIL_PASS")
SMTP_HOST = os.getenv("MAIL_HOST", "smtp.gmail.com")
# SMTP_PORT = int(os.getenv("MAIL_PORT", 587))
# 변경 (SMTP_SSL)
server = smtplib.SMTP_SSL('smtp.gmail.com', 465)