from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from seat_logic import calculate_good_seats 
import time, sys, io, json

# 표준 출력을 UTF-8로 설정
sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.detach(), encoding='utf-8')

def get_top_movie_title(driver, theater_name):
    """각 영화관 사이트의 무비차트 페이지에서 1위 제목을 추출합니다."""
    try:
        if theater_name == "CGV 강남":
            # 무비차트 전용 페이지로 접속
            driver.get("http://www.cgv.co.kr/movies/")
            # 제목이 보일 때까지 대기 (CSS 선택자 보강)
            element = WebDriverWait(driver, 15).until(
                EC.visibility_of_element_located((By.CSS_SELECTOR, ".sect-movie-chart .box-contents .title"))
            )
            return element.text.strip()

        elif theater_name == "롯데시네마 건대":
            driver.get("https://www.lottecinema.co.kr/NLCHS/Movie/List")
            # 롯데시네마는 리스트가 로딩되는 시간이 필요함
            element = WebDriverWait(driver, 15).until(
                EC.visibility_of_element_located((By.CSS_SELECTOR, ".screen_list .btm_info .tit"))
            )
            return element.text.strip()

        elif theater_name == "메가박스 코엑스":
            driver.get("https://www.megabox.co.kr/movie")
            # 메가박스는 영화 리스트의 tit 클래스 사용
            element = WebDriverWait(driver, 15).until(
                EC.visibility_of_element_located((By.CSS_SELECTOR, "#movieList .tit"))
            )
            return element.text.strip()
            
    except Exception as e:
        print(f"DEBUG: {theater_name} 추출 중 에러 발생: {str(e)}", file=sys.stderr)
        return f"{theater_name} 제목 추출 실패"
    return "알 수 없는 영화"

def crawl_theater_data():
    opts = Options()
    # opts.add_argument("--headless=new")
    opts.add_argument("--no-sandbox")
    opts.add_argument("--disable-dev-shm-usage")
    # 자동화 탐지 방지
    opts.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

    driver = webdriver.Chrome(options=opts)
   
    theaters = [
        {"name": "CGV 강남"},
        {"name": "롯데시네마 건대"},
        {"name": "메가박스 코엑스"}
    ]

    results = []
    
    try:
        for theater in theaters:
            # 1. 실제 사이트에서 인기 1위 영화 제목 가져오기
            top_title = get_top_movie_title(driver, theater["name"])
            
            # 2. 잔여 좌석 데이터 (실제 서비스 시 각 극장 상세 예매 페이지 크롤링 필요)
            # 여기서는 로직 테스트를 위해 가상의 실시간 좌석 데이터를 생성합니다.
            all_seats = [("C", 7), ("D", 8), ("E", 10), ("A", 1)] 
            
            # 3. 명당 잔여석 계산
            good_seats_count = calculate_good_seats(all_seats)
            
            results.append({
                "theater_name": theater["name"],
                "title": top_title,
                "good_seats": good_seats_count
            })

        # 최종 결과를 JSON 형태로 출력 (Java 백엔드가 읽어갈 값)
        print(json.dumps(results, ensure_ascii=False))

    except Exception as e:
        print(json.dumps({"error": str(e)}, ensure_ascii=False))
    finally:
        driver.quit()

if __name__ == "__main__":
    crawl_theater_data()