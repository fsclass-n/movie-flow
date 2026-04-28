import subprocess
import sys
import os

if __name__ == "__main__":
    # 현재 경로 기준으로 크롤러 실행
    script_path = os.path.join(os.path.dirname(__file__), "scripts", "theater_crawler.py")
    subprocess.run([sys.executable, script_path])