import sys
import smtplib
from email.message import EmailMessage
import os

# 인코딩 설정 (자바 로그 깨짐 방지)
import io
sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.detach(), encoding='utf-8')

try:
    from account import EMAIL_ADDRESS, EMAIL_PASSWORD, SMTP_HOST, SMTP_PORT
except ImportError:
    sys.path.append(os.path.dirname(os.path.abspath(__file__)))
    from account import EMAIL_ADDRESS, EMAIL_PASSWORD, SMTP_HOST, SMTP_PORT

def send_email(recipient, subject, body):
    if not EMAIL_ADDRESS or not EMAIL_PASSWORD:
        print("에러: 메일 계정 정보가 없습니다. account.py 또는 .env를 확인하세요.")
        sys.exit(1)

    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = EMAIL_ADDRESS
    msg["To"] = recipient
    msg.set_content(body)

    try:
        # SMTP_PORT가 문자열로 넘어올 경우를 대비해 int 변환
        with smtplib.SMTP(SMTP_HOST, int(SMTP_PORT)) as smtp:
            smtp.ehlo()
            smtp.starttls()
            # 로그인 시도
            smtp.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
            smtp.send_message(msg)
            print(f"성공: {recipient}에게 메일 발송 완료.")
    except smtplib.SMTPAuthenticationError:
        print(f"실패: 인증 오류! 구글 앱 비밀번호를 확인하세요. (현재 사용중: {EMAIL_PASSWORD[:4]}****)")
        sys.exit(1)
    except Exception as e:
        print(f"실패: 메일 발송 중 알 수 없는 에러 발생: {e}")
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("사용법: python send.py <recipient> <subject> <body>")
        sys.exit(1)

    target_email = sys.argv[1]
    mail_subject = sys.argv[2]
    mail_body = sys.argv[3]

    send_email(target_email, mail_subject, mail_body)