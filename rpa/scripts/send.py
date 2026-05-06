import sys
import smtplib
from email.message import EmailMessage
import os
import io

sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding='utf-8')

from account import EMAIL_ADDRESS, EMAIL_PASSWORD, SMTP_HOST, SMTP_PORT


def send_email(recipient, subject, body):

    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = EMAIL_ADDRESS
    msg["To"] = recipient
    msg.set_content(body)

    try:
        # 🔥 SSL 방식 (465)
        with smtplib.SMTP_SSL(SMTP_HOST, SMTP_PORT) as smtp:
            smtp.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
            smtp.send_message(msg)

        print("메일 발송 성공")

    except Exception as e:
        print(f"메일 실패: {e}")
        sys.exit(1)


if __name__ == "__main__":
    send_email(sys.argv[1], sys.argv[2], sys.argv[3])