# rpa/scripts/theater_crawler.py
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from seat_logic import calculate_good_seats 
import time, sys, io
import json # 결과를 구조화해서 출력하기 위해 추가

# 표준 출력을 UTF-8로 설정 (윈도우 한글 깨짐 방지 핵심)
sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.detach(), encoding='utf-8')


def crawl_theater_data():
    
    opts = Options()
    opts.add_argument("--headless=new")
    opts.add_argument("--no-sandbox")
    opts.add_argument("--disable-dev-shm-usage")
    
    driver = webdriver.Chrome(options=opts)
    
    theaters = [
        {"name": "CGV 강남", "url": "https://www.cgv.co.kr/..."},
        {"name": "롯데시네마 건대", "url": "https://www.lottecinema.co.kr/..."},
        {"name": "메가박스 코엑스", "url": "https://www.megabox.co.kr/..."}
    ]

    results = []
    
    try:
        for theater in theaters:
            # 실제 운영 시 URL 접속 및 데이터 파싱 로직이 들어갑니다.
            # driver.get(theater["url"])
            # time.sleep(2)
            
            # [테스트 가상 데이터]
            title = "인사이드 아웃 2" 
            # 실제로는 드라이버가 긁어온 좌석 리스트가 들어감
            all_seats = [("C", 7), ("D", 8), ("E", 10), ("A", 1)] 
            
            # seat_logic을 사용하여 명당 잔여석 계산
            good_seats_count = calculate_good_seats(all_seats)
            
            results.append({
                "theater_name": theater["name"],
                "title": title,
                "good_seats": good_seats_count
            })

        # DB에 직접 넣는 대신, 표준 출력(stdout)으로 JSON 데이터를 내보냄
        # 자바의 BufferedReader가 이 값을 읽어갑니다.
        print(json.dumps(results, ensure_ascii=False))

    except Exception as e:
        # 에러 발생 시 자바가 알 수 있도록 에러 메시지 출력
        print(json.dumps({"error": str(e)}))
    finally:
        driver.quit()

if __name__ == "__main__":
    crawl_theater_data()