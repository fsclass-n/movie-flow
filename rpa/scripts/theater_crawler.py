from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.options import Options
import time

def main():
    opts = Options()
    opts.add_argument("--headless=new")
    opts.add_argument("--no-sandbox")
    opts.add_argument("--disable-dev-shm-usage")

    driver = webdriver.Chrome(options=opts)
    try:
        driver.get("https://example-theater-site.com")  # 대표 영화관 사이트 (실제로는 API/크롤링 허용 사이트 사용)
        time.sleep(3)

        # 예: 상영시간 및 좌석 정보가 있는 테이블
        titles = driver.find_elements(By.CSS_SELECTOR, "div.movie-title")
        times = driver.find_elements(By.CSS_SELECTOR, "div.showtime")

        print("=== 상영정보 수집 결과 ===")
        for title, tm in zip(titles, times):
            print(f"{title.text} | {tm.text}")

        # 결과를 파일로 적어 두면 Java 쪽에서 JSON/파일로 읽어들여도 됨
        with open("rpa/schedule_output.txt", "w", encoding="utf-8") as f:
            for title, tm in zip(titles, times):
                f.write(f"{title.text} | {tm.text}\n")

    finally:
        driver.quit()

if __name__ == "__main__":
    main()