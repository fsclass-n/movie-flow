import subprocess
import sys
import os

def run_engine():
    """
    RPA 엔진 실행 메인 함수.
    stdout(JSON)을 통해 백엔드(Java)와 통신
    """

    current_dir = os.path.dirname(os.path.abspath(__file__))
    script_path = os.path.join(current_dir, "scripts", "theater_crawler.py")

    try:
        result = subprocess.run(
            [sys.executable, script_path],
            capture_output=True,
            text=True,
            encoding="utf-8",
            timeout=30
        )

        if result.returncode == 0:
            print(result.stdout)
        else:
            print(f"Error: {result.stderr}")

    except subprocess.TimeoutExpired:
        print("Error: RPA execution timeout")


if __name__ == "__main__":
    run_engine()