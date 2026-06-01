from seat_logic import calculate_good_seats, get_premium_seats
from bs4 import BeautifulSoup
from datetime import datetime, timedelta
import html
import base64
import hashlib
import hmac
import io
import json
import math
import os
import random
import re
import sys
import urllib.parse
from playwright.sync_api import sync_playwright

# 출력 인코딩 설정 (한글 깨짐 방지)
sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding="utf-8")
sys.stderr = io.TextIOWrapper(sys.stderr.detach(), encoding="utf-8")

def log_stderr(message):
    print(message, file=sys.stderr)

def clean_movie_title(text):
    if not text: return ""
    title = re.sub(r"\s+", " ", str(text)).strip()
    title = title.replace("라스트", "").strip()
    title = re.sub(r"^(전체|ALL|12|15|청불)\s*", "", title, flags=re.IGNORECASE).strip()
    return title

def to_int(value, default=0):
    if value is None: return default
    match = re.search(r"-?\d+", str(value).replace(",", ""))
    return int(match.group(0)) if match else default

def choose_best_schedule(schedules):
    if not schedules: return None
    now = datetime.now() - timedelta(minutes=10)
    valid = [s for s in schedules if s["start_dt"] >= now and s["remaining_seats"] > 0]
    if not valid: valid = schedules
    return sorted(valid, key=lambda x: (x["start_dt"], x["rank"]))[0]

def build_schedule(theater_name, title, date_str, time_str, remaining_seats, total_seats, rank=999, image_url=""):
    raw_date = str(date_str).replace(".", "-").replace("/", "-")
    if re.fullmatch(r"\d{8}", raw_date):
        base = datetime.strptime(raw_date, "%Y%m%d")
    else:
        base = datetime.strptime(raw_date[:10], "%Y-%m-%d")
        
    parts = str(time_str).split(":")
    hour, minute = int(parts[0]), int(parts[1])
    base += timedelta(days=hour // 24)
    hour %= 24
    
    # KST 기준 시간 설정
    start_dt = base.replace(hour=hour, minute=minute)
    showing_date = base.strftime("%Y-%m-%d")
    showing_time = f"{hour:02d}:{minute:02d}"
    
    t_seats = max(1, to_int(total_seats))
    r_seats = max(0, min(t_seats, to_int(remaining_seats)))
    
    return {
        "theater_name": theater_name,
        "title": clean_movie_title(title),
        "showing_date": showing_date,
        "showing_time": showing_time,
        "start_dt": start_dt,
        "remaining_seats": r_seats,
        "total_seats": t_seats,
        "rank": rank,
        "image_url": image_url,
    }

def get_seat_layout(theater_name, total_seats=0):
    seats_per_row = 25 if (total_seats > 200 or "롯데" in theater_name) else 15
    row_count = max(1, math.ceil(total_seats / seats_per_row)) if total_seats else 10
    rows = [chr(ord("A") + i) for i in range(min(row_count, 20))]
    return rows, seats_per_row

def build_available_seats(theater_name, remaining_seats, total_seats):
    rows, seats_per_row = get_seat_layout(theater_name, total_seats)
    total = max(1, total_seats)
    all_seats = [(r, n) for r in rows for n in range(1, seats_per_row + 1)][:total]
    
    seed = f"{theater_name}:{total_seats}"
    random.seed(seed)
    selected = random.sample(all_seats, min(len(all_seats), remaining_seats))
    return sorted(selected, key=lambda x: (x[0], x[1]))

def fallback_schedule(theater):
    now = datetime.now() + timedelta(hours=2)
    return {
        "theater_name": theater["name"], "title": "상영 정보 확인 중",
        "showing_date": now.strftime("%Y-%m-%d"), "showing_time": now.strftime("%H:%M"),
        "remaining_seats": 100, "total_seats": 150, "image_url": ""
    }

def crawl_theater_data():
    import requests
    theaters = [
        {"name": "롯데시네마 건대", "crawler": "lotte", "cinema_id": "1|0001|1004"},
        {"name": "메가박스 코엑스", "crawler": "megabox", "brch_no": "1351"},
        {"name": "CGV 강남", "crawler": "cgv", "theater_code": "0056"},
    ]
    
    results = []
    
    for theater in theaters:
        schedule = None
        try:
            if theater["crawler"] == "lotte":
                log_stderr(f"Crawling Lotte: {theater['name']}...")
                schedules = []
                today = datetime.now()
                for offset in range(3):
                    play_date = (today + timedelta(days=offset)).strftime("%Y-%m-%d")
                    payload = {
                        "MethodName": "GetPlaySequence",
                        "channelType": "HO",
                        "osType": "W",
                        "osVersion": "Mozilla/5.0",
                        "playDate": play_date,
                        "cinemaID": theater["cinema_id"],
                        "representationMovieCode": ""
                    }
                    
                    res = requests.post(
                        "https://www.lottecinema.co.kr/LCWS/Ticketing/TicketingData.aspx", 
                        data={"paramList": json.dumps(payload)},
                        headers={"User-Agent": "Mozilla/5.0"},
                        timeout=15
                    )
                    data = res.json()
                    
                    items = data.get("PlaySeqs", {}).get("Items", [])
                    for item in items:
                        t_seats = to_int(item.get("TotalSeatCount"))
                        poster_url = item.get('PosterURL', '')
                        if poster_url.startswith("http"):
                            img_url = poster_url
                        else:
                            img_url = f"https://cdn.lottecinema.co.kr/{poster_url}" if poster_url else ""
                            
                        schedules.append(build_schedule(
                            theater["name"],
                            item.get("MovieNameKR", ""),
                            item.get("PlayDt", play_date),
                            item.get("StartTime", ""),
                            t_seats - to_int(item.get("BookingSeatCount")),
                            t_seats,
                            to_int(item.get("BookingSortSequence", 999)),
                            img_url
                        ))
                    # 오늘자 상영 정보 중 유효한 미래 상영 일정이 존재하면 내일/모레 일정 조회 생략
                    now_threshold = datetime.now() - timedelta(minutes=10)
                    valid_today = [s for s in schedules if s["start_dt"] >= now_threshold and s["remaining_seats"] > 0]
                    if valid_today:
                        break
                schedule = choose_best_schedule(schedules)
                
            elif theater["crawler"] == "megabox":
                log_stderr(f"Crawling Megabox: {theater['name']}...")
                schedules = []
                today = datetime.now()
                for offset in range(3):
                    play_date = (today + timedelta(days=offset)).strftime("%Y%m%d")
                    payload = {
                        "masterType": "brch",
                        "detailType": "area",
                        "brchNo": theater["brch_no"],
                        "playDe": play_date
                    }
                    
                    res = requests.post(
                        "https://www.megabox.co.kr/on/oh/ohc/Brch/schedulePage.do",
                        json=payload,
                        headers={"User-Agent": "Mozilla/5.0"},
                        timeout=15
                    )
                    data = res.json()
                    
                    items = data.get("megaMap", {}).get("movieFormList", [])
                    for item in items:
                        schedules.append(build_schedule(
                            theater["name"],
                            item.get("movieNm", ""),
                            item.get("playDe", play_date),
                            item.get("playStartTime", ""),
                            item.get("restSeatCnt"),
                            item.get("totSeatCnt"),
                            to_int(item.get("boxoRank", 999)),
                            f"https://file.megabox.co.kr{item.get('posterPath')}" if item.get('posterPath') else ""
                        ))
                    # 오늘자 상영 정보 중 유효한 미래 상영 일정이 존재하면 내일/모레 일정 조회 생략
                    now_threshold = datetime.now() - timedelta(minutes=10)
                    valid_today = [s for s in schedules if s["start_dt"] >= now_threshold and s["remaining_seats"] > 0]
                    if valid_today:
                        break
                schedule = choose_best_schedule(schedules)
                
            elif theater["crawler"] == "cgv":
                log_stderr(f"Crawling CGV: {theater['name']}...")
                with sync_playwright() as p:
                    browser = p.chromium.launch(
                        headless=True,
                        args=["--disable-blink-features=AutomationControlled", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--single-process"]
                    )
                    context = browser.new_context(
                        user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                    page = context.new_page()
                    
                    new_code = f"{theater['theater_code']}001"
                    url = f"https://cgv.co.kr/cnm/bzplcCgv/{new_code}"
                    
                    try:
                        page.goto(url, timeout=60000)
                        page.wait_for_load_state("networkidle", timeout=10000)
                    except:
                        pass
                    
                    tab = page.locator("text='상영시간표'").first
                    try:
                        tab.wait_for(state="visible", timeout=10000)
                    except:
                        pass
                        
                    if tab.is_visible():
                        tab.click()
                        try:
                            page.wait_for_selector(".accordion_container__W7nEs", timeout=10000)
                        except:
                            pass
                        
                        html_text = page.locator("body").inner_html()
                        soup = BeautifulSoup(html_text, "html.parser")
                        
                        schedules = []
                        today = datetime.now()
                        play_de = today.strftime("%Y%m%d")
                        
                        for block in soup.select(".accordion_container__W7nEs"):
                            title_tag = block.select_one("span.title2")
                            if not title_tag: continue
                            title = title_tag.get_text().strip()
                            
                            img_tag = block.select_one("img.screenInfo_poster__E_NVQ")
                            image_url = img_tag.get("src", "") if img_tag else ""
                            
                            for time_item in block.select("li.screenInfo_timeItem__y8ZXg"):
                                start_tag = time_item.select_one(".screenInfo_start__6BZbu")
                                if not start_tag: continue
                                time_txt = start_tag.get_text().strip()
                                
                                hall_tag = time_item.select_one(".screenInfo_theater__yHSZ8")
                                hall_name = hall_tag.get_text().strip() if hall_tag else ""
                                
                                status_tag = time_item.select_one(".screenInfo_status__lT4zd")
                                if not status_tag: continue
                                
                                remaining = 0
                                total = 150
                                status_text = status_tag.get_text().strip()
                                
                                if "예매종료" in status_text or "매진" in status_text:
                                    remaining = 0
                                    total_match = re.search(r"/\s*(\d+)", status_text)
                                    if total_match:
                                        total = int(total_match.group(1))
                                else:
                                    rem_tag = status_tag.select_one(".c-blue, .c-red")
                                    if rem_tag:
                                        remaining = to_int(rem_tag.get_text())
                                    total_match = re.search(r"/\s*(\d+)", status_text)
                                    if total_match:
                                        total = int(total_match.group(1))
                                        
                                schedules.append(build_schedule(
                                    theater["name"], title, play_de, time_txt, remaining, total,
                                    image_url=image_url
                                ))
                        schedule = choose_best_schedule(schedules)
                    else:
                        log_stderr("상영시간표 tab not found!")
                        
                    browser.close()
                        
        except Exception as e:
            log_stderr(f"Error crawling {theater['name']}: {e}")
            
        if not schedule:
            log_stderr(f"Using fallback for {theater['name']}")
            schedule = fallback_schedule(theater)
            
        available_seats = build_available_seats(theater["name"], schedule["remaining_seats"], schedule["total_seats"])
        rows, seats_per_row = get_seat_layout(theater["name"], schedule["total_seats"])
        
        premium_seats = get_premium_seats(len(rows), seats_per_row)
        good_seats_count = calculate_good_seats(available_seats, premium_seats)
        
        results.append({
            "theater_name": theater["name"],
            "title": schedule["title"],
            "start_time": f"{schedule['showing_date']} {schedule['showing_time']}",
            "remaining_seats": schedule["remaining_seats"],
            "total_seats": schedule["total_seats"],
            "good_seats": good_seats_count,
            "available_seats": available_seats,
            "image_url": schedule.get("image_url", ""),
            "crawled_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        })
        
    print(json.dumps(results, ensure_ascii=False))

if __name__ == "__main__":
    crawl_theater_data()