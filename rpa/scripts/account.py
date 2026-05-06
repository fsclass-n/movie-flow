import os
from dotenv import load_dotenv

load_dotenv()

EMAIL_ADDRESS = os.getenv("MAIL_USER")
EMAIL_PASSWORD = os.getenv("MAIL_PASS")

SMTP_HOST = os.getenv("MAIL_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("MAIL_PORT", 465))