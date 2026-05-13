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
import time
import urllib.parse
import urllib.request
import requests

# 출력 인코딩 설정 (한글 깨짐 방지)
sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding="utf-8")
sys.stderr = io.TextIOWrapper(sys.stderr.detach(), encoding="utf-8")

# URL 설정
MEGABOX_SCHEDULE_URL = "https://www.megabox.co.kr/on/oh/ohc/Brch/schedulePage.do"
LOTTE_TICKETING_URL = "https://www.lottecinema.co.kr/LCWS/Ticketing/TicketingData.aspx"
REQUEST_TIMEOUT = 20

# [수정] CGV 세션 및 헤더 강화 (403 Forbidden 해결용)
CGV_SESSION = requests.Session()
CGV_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
    "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
    "Accept-Encoding": "gzip, deflate, br",
    "Connection": "keep-alive",
    "Upgrade-Insecure-Requests": "1",
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
}

def normalize_text(text):
    if not text: return ""
    return re.sub(r"\s+", " ", html.unescape(str(text)).replace("\r", " ").replace("\n", " ")).strip()

def is_valid_title(text):
    cleaned = normalize_text(text)
    if not cleaned or len(cleaned) > 80: return False
    bad_keywords = ["AD", "광고", "예매", "예고", "로그인", "회원", "이벤트", "혜택", "쿠폰"]
    if any(keyword in cleaned for keyword in bad_keywords): return False
    return True

def clean_movie_title(text):
    title = normalize_text(text)
    title = title.replace("라스트", "").strip()
    title = re.sub(r"^(전체|ALL|12|15|청불)\s*", "", title, flags=re.IGNORECASE).strip()
    return title

def to_int(value, default=0):
    if value is None: return default
    match = re.search(r"-?\d+", str(value).replace(",", ""))
    return int(match.group(0)) if match else default

def date_candidates(days=3): # 배포 환경 속도를 위해 후보 날짜를 3일로 조정
    today = datetime.now().date()
    return [today + timedelta(days=offset) for offset in range(days)]

def normalize_show_datetime(date_value, time_value):
    raw_date = str(date_value).replace(".", "-").replace("/", "-")
    if re.fullmatch(r"\d{8}", raw_date):
        base = datetime.strptime(raw_date, "%Y%m%d")
    else:
        base = datetime.strptime(raw_date[:10], "%Y-%m-%d")
    
    parts = str(time_value).split(":")
    hour, minute = int(parts[0]), int(parts[1])
    base += timedelta(days=hour // 24)
    hour %= 24
    return base, base.strftime("%Y-%m-%d"), f"{hour:02d}:{minute:02d}"

def absolutize_url(url, base_url):
    if not url: return ""
    url = str(url).strip()
    if url.startswith("//"): return "https:" + url
    return urllib.parse.urljoin(base_url, url)

def first_image_from_value(value):
    if not value: return ""
    if isinstance(value, str) and re.search(r"\.(jpg|jpeg|png|webp)", value, re.IGNORECASE):
        return value
    if isinstance(value, (dict, list)):
        for val in (value.values() if isinstance(value, dict) else value):
            found = first_image_from_value(val)
            if found: return found
    return ""

def post_json(url, payload, referer):
    headers = {
        "Content-Type": "application/json;charset=UTF-8",
        "Referer": referer,
        "User-Agent": CGV_HEADERS["User-Agent"]
    }
    response = requests.post(url, json=payload, headers=headers, timeout=REQUEST_TIMEOUT)
    return response.json()

def post_lotte(payload):
    url = LOTTE_TICKETING_URL
    headers = {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "Referer": "https://www.lottecinema.co.kr/NLCHS/Ticketing",
        "User-Agent": CGV_HEADERS["User-Agent"]
    }
    data = {"paramList": json.dumps(payload, ensure_ascii=False)}
    response = requests.post(url, data=data, headers=headers, timeout=REQUEST_TIMEOUT)
    return response.json()

def choose_best_schedule(schedules):
    if not schedules: return None
    now = datetime.now() - timedelta(minutes=10)
    # 현재 시간 이후 상영 중 잔여 좌석이 있는 것 우선
    valid = [s for s in schedules if s["start_dt"] >= now and s["remaining_seats"] > 0]
    if not valid: valid = schedules
    return sorted(valid, key=lambda x: (x["start_dt"], x["rank"]))[0]

def is_premium_position(row, seat_number, seats_per_row):
    if row not in ["C", "D", "E", "F"]: return False
    center = (seats_per_row + 1) / 2
    return (center - 3) <= seat_number <= (center + 3)

def build_schedule(theater_name, title, date_value, time_value, remaining_seats, total_seats, rank=999, image_url=""):
    start_dt, showing_date, showing_time = normalize_show_datetime(date_value, time_value)
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

def crawl_megabox_schedule(theater):
    schedules = []
    for play_date in date_candidates():
        play_de = play_date.strftime("%Y%m%d")
        payload = {"masterType": "brch", "detailType": "area", "brchNo": theater["brch_no"], "playDe": play_de}
        try:
            data = post_json(MEGABOX_SCHEDULE_URL, payload, "https://www.megabox.co.kr/booking/timetable")
            items = data.get("megaMap", {}).get("movieFormList", [])
            for item in items:
                schedules.append(build_schedule(
                    theater["name"], item.get("movieNm", ""), item.get("playDe", play_de),
                    item.get("playStartTime", ""), item.get("restSeatCnt"), item.get("totSeatCnt"),
                    to_int(item.get("boxoRank", 999)),
                    absolutize_url(first_image_from_value(item), "https://www.megabox.co.kr/")
                ))
        except: continue
    return choose_best_schedule(schedules)

def crawl_lotte_schedule(theater):
    schedules = []
    for play_date in date_candidates():
        play_dt = play_date.strftime("%Y-%m-%d")
        payload = {"MethodName": "GetPlaySequence", "channelType": "HO", "osType": "W", "playDate": play_dt, "cinemaID": theater["cinema_id"]}
        try:
            data = post_lotte(payload)
            items = data.get("PlaySeqs", {}).get("Items", [])
            for item in items:
                t_seats = to_int(item.get("TotalSeatCount"))
                schedules.append(build_schedule(
                    theater["name"], item.get("MovieNameKR", ""), item.get("PlayDt", play_dt),
                    item.get("StartTime", ""), t_seats - to_int(item.get("BookingSeatCount")), t_seats,
                    to_int(item.get("BookingSortSequence", 999)),
                    absolutize_url(first_image_from_value(item), "https://www.lottecinema.co.kr/")
                ))
        except: continue
    return choose_best_schedule(schedules)

# [수정] CGV 크롤링 로직 (Requests 403 우회 강화)
def fetch_cgv_with_requests(theater_code, play_de):
    main_url = f"https://www.cgv.co.kr/theaters/?areacode=01&theaterCode={theater_code}&date={play_de}"
    # 1. 메인 페이지 접속하여 쿠키 획득
    CGV_SESSION.get(main_url, headers=CGV_HEADERS, timeout=REQUEST_TIMEOUT)
    
    # 2. iframe 데이터 요청 (Referer 필수)
    iframe_url = f"https://www.cgv.co.kr/common/showtimes/iframeTheater.aspx?theatercode={theater_code}&date={play_de}"
    headers = CGV_HEADERS.copy()
    headers["Referer"] = main_url
    
    response = CGV_SESSION.get(iframe_url, headers=headers, timeout=REQUEST_TIMEOUT)
    response.raise_for_status()
    return response.text

def parse_cgv_html(theater, play_de, html_text):
    schedules = []
    soup = BeautifulSoup(html_text, "html.parser")
    for block in soup.select(".col-times"):
        title_tag = block.select_one("a strong")
        if not title_tag: continue
        title = title_tag.get_text().strip()
        img_tag = block.find_previous("div", class_="thumb-image")
        image_url = ""
        if img_tag and img_tag.find("img"):
            image_url = img_tag.find("img").get("src", "")

        for hall in block.select(".type-hall"):
            hall_info = hall.get_text()
            total_seats = to_int(re.search(r"총\s*(\d+)석", hall_info).group(1)) if "총" in hall_info else 150
            for info in hall.find_next_sibling("div", class_="info-timetable").select("li"):
                time_txt = info.select_one("em").get_text() if info.select_one("em") else ""
                seat_txt = info.select_one("span").get_text() if info.select_one("span") else ""
                if time_txt and "잔여" in seat_txt:
                    remaining = to_int(re.search(r"(\d+)석", seat_txt).group(1))
                    schedules.append(build_schedule(
                        theater["name"], title, play_de, time_txt, remaining, total_seats,
                        image_url=absolutize_url(image_url, "https://www.cgv.co.kr/")
                    ))
    return schedules

def crawl_cgv_schedule(theater):
    for play_date in date_candidates():
        play_de = play_date.strftime("%Y%m%d")
        try:
            html_text = fetch_cgv_with_requests(theater["theater_code"], play_de)
            parsed = parse_cgv_html(theater, play_de, html_text)
            if parsed:
                return choose_best_schedule(parsed)
        except Exception as e:
            print(f"CGV {play_de} 시도 실패: {e}", file=sys.stderr)
            continue
    return None

def get_seat_layout(theater_name, total_seats=0):
    seats_per_row = 25 if (total_seats > 200 or "롯데" in theater_name) else 15
    row_count = max(1, math.ceil(total_seats / seats_per_row)) if total_seats else 10
    rows = [chr(ord("A") + i) for i in range(min(row_count, 20))]
    return rows, seats_per_row

def build_available_seats(theater_name, remaining_seats, total_seats):
    rows, seats_per_row = get_seat_layout(theater_name, total_seats)
    total = max(1, total_seats)
    # 실제 좌석 배열을 흉내내는 로직 (기존 유지)
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
    theaters = [
        {"name": "롯데시네마 건대", "crawler": "lotte", "cinema_id": "1|0001|1004"},
        {"name": "메가박스 코엑스", "crawler": "megabox", "brch_no": "1351"},
        {"name": "CGV 강남", "crawler": "cgv", "theater_code": "0056"},
    ]
    results = []
    for theater in theaters:
        try:
            if theater["crawler"] == "lotte": schedule = crawl_lotte_schedule(theater)
            elif theater["crawler"] == "megabox": schedule = crawl_megabox_schedule(theater)
            else: schedule = crawl_cgv_schedule(theater)
            
            if not schedule: schedule = fallback_schedule(theater)
        except Exception as e:
            print(f"{theater['name']} 에러: {e}", file=sys.stderr)
            schedule = fallback_schedule(theater)

        available_seats = build_available_seats(theater["name"], schedule["remaining_seats"], schedule["total_seats"])
        rows, seats_per_row = get_seat_layout(theater["name"], schedule["total_seats"])
        
        # 외부 함수 호출 (seat_logic.py 필수)
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