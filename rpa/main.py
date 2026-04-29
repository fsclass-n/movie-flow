# rpa/main.py
import subprocess
import sys
import os
import mysql.connector

def log_to_db(message):
    # 백엔드 관리자 페이지의 'RPA Engine Active' 로그를 위해 DB에 기록
    try:
        conn = mysql.connector.connect(host="...", user="...", password="...", database="movieflow")
        cursor = conn.cursor()
        cursor.execute("INSERT INTO rpa_logs (message) VALUES (%s)", (message,))
        conn.commit()
        conn.close()
    except:
        pass

if __name__ == "__main__":
    log_to_db("RPA 스크립트 실행 시작")
    
    script_path = os.path.join(os.path.dirname(__file__), "scripts", "theater_crawler.py")
    result = subprocess.run([sys.executable, script_path], capture_output=True, text=True)
    
    if result.returncode == 0:
        log_to_db("RPA 크롤링 및 DB 업데이트 성공")
    else:
        log_to_db(f"RPA 에러 발생: {result.stderr}")