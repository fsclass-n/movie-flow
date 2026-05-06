import subprocess
import sys
import os

def run_engine():
    """
    RPA 엔진 실행 메인 함수.
    DB 연결 없이 표준 출력(stdout)을 통해 백엔드와 통신합니다.
    """
    # theater_crawler.py 경로 설정
    current_dir = os.path.dirname(os.path.abspath(__file__))
    script_path = os.path.join(current_dir, "scripts", "theater_crawler.py")
    
    # 크롤러 실행
    # sys.executable을 사용하여 현재 가상환경의 파이썬을 그대로 사용합니다.
    result = subprocess.run([sys.executable, script_path], capture_output=True, text=True, encoding='utf-8')
    
    if result.returncode == 0:
        # 크롤러가 출력한 JSON 데이터를 그대로 다시 출력 (Java에서 읽음)
        print(result.stdout)
    else:
        # 에러 발생 시 stderr 내용을 출력
        print(f"Error: {result.stderr}")

if __name__ == "__main__":
    run_engine()