# rpa/scripts/theater_crawler.py
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from seat_logic import calculate_good_seats # 명당 로직 불러오기
import mysql.connector # DB 업데이트용
import time

def get_db_connection():
    return mysql.connector.connect(
        host="your-tidb-host", # 백엔드와 동일한 설정
        user="your-username",
        password="your-password",
        database="movieflow",
        port=4000
    )

def crawl_theater_data():
    opts = Options()
    opts.add_argument("--headless=new")
    driver = webdriver.Chrome(options=opts)
    
    # 크롤링할 대상 정의 (프론트 결과와 일치)
    theaters = [
        {"name": "CGV 강남", "url": "https://www.cgv.co.kr/..."},
        {"name": "롯데시네마 건대", "url": "https://www.lottecinema.co.kr/..."},
        {"name": "메가박스 코엑스", "url": "https://www.megabox.co.kr/..."}
    ]

    results = []
    
    try:
        for theater in theaters:
            driver.get(theater["url"])
            time.sleep(2) # 로딩 대기
            
            # [가정] 페이지에서 영화 제목과 모든 좌석 데이터를 가져옴
            # 실제 구현 시 각 영화관 사이트의 Selector에 맞게 수정 필요
            title = "인사이드 아웃 2" # 예시
            all_seats = [("C", 7), ("D", 8), ("A", 1)] # 예시 좌석 리스트
            
            # seat_logic을 사용하여 명당 잔여석 계산
            good_seats_count = calculate_good_seats(all_seats)
            
            results.append({
                "theater_name": theater["name"],
                "title": title,
                "good_seats": good_seats_count
            })

        # DB 업데이트 (백엔드 화면에 즉시 반영)
        update_database(results)

    finally:
        driver.quit()

def update_database(results):
    conn = get_db_connection()
    cursor = conn.cursor()
    for res in results:
        sql = "UPDATE movies SET good_seats = ? WHERE title = ? AND theater_name = ?"
        cursor.execute(sql, (res["good_seats"], res["title"], res["theater_name"]))
    conn.commit()
    cursor.close()
    conn.close()
    print("RPA: DB 업데이트 완료")

if __name__ == "__main__":
    crawl_theater_data()