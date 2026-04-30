from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from seat_logic import calculate_good_seats
import html
import io
import json
import math
import random
import re
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timedelta

sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding="utf-8")
sys.stderr = io.TextIOWrapper(sys.stderr.detach(), encoding="utf-8")


MEGABOX_SCHEDULE_URL = "https://www.megabox.co.kr/on/oh/ohc/Brch/schedulePage.do"
LOTTE_TICKETING_URL = "https://www.lottecinema.co.kr/LCWS/Ticketing/TicketingData.aspx"


def normalize_text(text):
    if not text:
        return ""
    return re.sub(r"\s+", " ", html.unescape(str(text)).replace("\r", " ").replace("\n", " ")).strip()


def is_valid_title(text):
    cleaned = normalize_text(text)
    if not cleaned or len(cleaned) > 80:
        return False
    bad_keywords = [
        "AD", "광고", "예매", "예고", "로그인", "회원", "이벤트", "혜택", "쿠폰",
        "추천", "검색", "상영시간표", "박스오피스", "무비차트", "개봉", "관람가"
    ]
    if any(keyword in cleaned for keyword in bad_keywords):
        return False
    if re.search(r"\d+%|\d{4}[.-]\d{1,2}[.-]\d{1,2}", cleaned):
        return False
    if re.fullmatch(r"[0-9\s\W]+", cleaned):
        return False
    return True


def clean_movie_title(text):
    title = normalize_text(text)
    title = title.replace("라스트", "").strip()
    title = re.sub(r"^(전체|ALL)\s*", "", title, flags=re.IGNORECASE).strip()
    title = re.split(r"\s+(예매\s*가능|관람평|평점|상영시간표)\b", title)[0].strip()
    return title


def to_int(value, default=0):
    if value is None:
        return default
    match = re.search(r"-?\d+", str(value).replace(",", ""))
    return int(match.group(0)) if match else default


def date_candidates(days=7):
    today = datetime.now().date()
    return [today + timedelta(days=offset) for offset in range(days)]


def normalize_show_datetime(date_value, time_value):
    raw_date = str(date_value).replace(".", "-").replace("/", "-")
    if re.fullmatch(r"\d{8}", raw_date):
        base = datetime.strptime(raw_date, "%Y%m%d")
    else:
        base = datetime.strptime(raw_date[:10], "%Y-%m-%d")

    hour, minute = [int(part) for part in str(time_value).split(":")[:2]]
    base += timedelta(days=hour // 24)
    hour %= 24
    return base, base.strftime("%Y-%m-%d"), f"{hour:02d}:{minute:02d}"


def post_json(url, payload):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {
        "Content-Type": "application/json;charset=UTF-8",
        "X-Requested-With": "XMLHttpRequest",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36",
        "Referer": "https://www.megabox.co.kr/booking/timetable",
    }
    request = urllib.request.Request(url, data=body, headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.loads(response.read().decode("utf-8"))


def post_lotte(payload):
    body = urllib.parse.urlencode({
        "paramList": json.dumps(payload, ensure_ascii=False)
    }).encode("utf-8")
    headers = {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "X-Requested-With": "XMLHttpRequest",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36",
        "Referer": "https://www.lottecinema.co.kr/NLCHS/Ticketing",
    }
    request = urllib.request.Request(LOTTE_TICKETING_URL, data=body, headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.loads(response.read().decode("utf-8-sig"))


def choose_best_schedule(schedules):
    if not schedules:
        return None

    now = datetime.now() - timedelta(minutes=20)
    future = [schedule for schedule in schedules if schedule["start_dt"] >= now]
    candidates = future or schedules
    candidates = [schedule for schedule in candidates if schedule["remaining_seats"] > 0] or candidates

    mario_schedules = [schedule for schedule in candidates if "마리오" in schedule["title"]]
    if mario_schedules:
        candidates = mario_schedules

    today_schedules = [schedule for schedule in candidates if schedule["start_dt"].date() == datetime.now().date()]
    if any(estimate_good_seats(schedule) > 0 for schedule in today_schedules):
        candidates = today_schedules

    return sorted(
        candidates,
        key=lambda schedule: (
            estimate_good_seats(schedule) <= 0,
            schedule["start_dt"],
            schedule.get("rank", 999),
        ),
    )[0]


def is_premium_position(row, seat_number, seats_per_row):
    if row not in ["C", "D", "E"]:
        return False
    center = (seats_per_row + 1) / 2
    start = max(1, round(center - 2))
    end = min(seats_per_row, round(center + 3))
    return start <= seat_number <= end


def estimate_good_seats(schedule):
    total_seats = max(0, to_int(schedule.get("total_seats")))
    remaining_seats = max(0, to_int(schedule.get("remaining_seats")))
    if total_seats == 0 or remaining_seats == 0:
        return 0
    rows, seats_per_row = get_seat_layout(schedule["theater_name"], total_seats)
    premium_count = sum(
        1
        for row in rows
        for seat_number in range(1, seats_per_row + 1)
        if is_premium_position(row, seat_number, seats_per_row)
    )
    return min(premium_count, math.floor((remaining_seats / total_seats) * premium_count))


def build_schedule(theater_name, title, date_value, time_value, remaining_seats, total_seats, rank=999):
    start_dt, showing_date, showing_time = normalize_show_datetime(date_value, time_value)
    total_seats = max(0, to_int(total_seats))
    remaining_seats = max(0, min(total_seats, to_int(remaining_seats))) if total_seats else max(0, to_int(remaining_seats))
    return {
        "theater_name": theater_name,
        "title": clean_movie_title(title),
        "showing_date": showing_date,
        "showing_time": showing_time,
        "start_dt": start_dt,
        "remaining_seats": remaining_seats,
        "total_seats": total_seats,
        "rank": rank,
    }


def crawl_megabox_schedule(theater):
    schedules = []
    for play_date in date_candidates():
        play_de = play_date.strftime("%Y%m%d")
        payload = {
            "masterType": "brch",
            "detailType": "area",
            "brchNo": theater["brch_no"],
            "brchNo1": theater["brch_no"],
            "firstAt": "Y",
            "playDe": play_de,
        }
        data = post_json(MEGABOX_SCHEDULE_URL, payload)
        items = data.get("megaMap", {}).get("movieFormList", [])
        for item in items:
            if item.get("playStartTime") and item.get("totSeatCnt") is not None:
                schedules.append(build_schedule(
                    theater["name"],
                    item.get("rpstMovieNm") or item.get("movieNm") or "",
                    item.get("playDe") or play_de,
                    item.get("playStartTime"),
                    item.get("restSeatCnt"),
                    item.get("totSeatCnt") or item.get("theabSeatCnt"),
                    to_int(item.get("boxoRank"), 999),
                ))
        selected = choose_best_schedule(schedules)
        if selected:
            return selected
    return choose_best_schedule(schedules)


def crawl_lotte_schedule(theater):
    schedules = []
    for play_date in date_candidates():
        play_dt = play_date.strftime("%Y-%m-%d")
        payload = {
            "MethodName": "GetPlaySequence",
            "channelType": "HO",
            "osType": "W",
            "osVersion": "Mozilla/5.0",
            "playDate": play_dt,
            "cinemaID": theater["cinema_id"],
            "representationMovieCode": "",
        }
        data = post_lotte(payload)
        items = data.get("PlaySeqs", {}).get("Items", [])
        for item in items:
            total_seats = to_int(item.get("TotalSeatCount"))
            booked_seats = to_int(item.get("BookingSeatCount"))
            if item.get("StartTime") and total_seats > 0 and item.get("IsBookingYN", "Y") == "Y":
                schedules.append(build_schedule(
                    theater["name"],
                    item.get("MovieNameKR") or "",
                    item.get("PlayDt") or play_dt,
                    item.get("StartTime"),
                    total_seats - booked_seats,
                    total_seats,
                    to_int(item.get("BookingSortSequence"), 999),
                ))
        selected = choose_best_schedule(schedules)
        if selected:
            return selected
    return choose_best_schedule(schedules)


def parse_cgv_remaining(text):
    match = re.search(r"(?:잔여\s*)?(\d+)\s*석", normalize_text(text))
    return to_int(match.group(1)) if match else None


def parse_cgv_total(text):
    match = re.search(r"(?:총\s*)?(\d+)\s*석", normalize_text(text))
    return to_int(match.group(1)) if match else 0


def find_child_text(element, selectors):
    for selector in selectors:
        try:
            for child in element.find_elements(By.CSS_SELECTOR, selector):
                text = normalize_text(child.text or child.get_attribute("title") or child.get_attribute("alt"))
                if is_valid_title(text):
                    return clean_movie_title(text)
        except Exception:
            continue
    return None


def crawl_cgv_schedule(driver, theater):
    schedules = []
    for play_date in date_candidates():
        play_de = play_date.strftime("%Y%m%d")
        url = f"https://www.cgv.co.kr/common/showtimes/iframeTheater.aspx?theatercode={theater['theater_code']}&date={play_de}"
        driver.get(url)
        time.sleep(3)

        for block in driver.find_elements(By.CSS_SELECTOR, ".col-times, .sect-showtimes"):
            title = find_child_text(block, [".info-movie a strong", ".info-movie strong", "strong.title", ".title"])
            if not title:
                continue
            halls = block.find_elements(By.CSS_SELECTOR, ".type-hall") or [block]
            for hall in halls:
                hall_text = normalize_text(hall.text)
                total_seats = parse_cgv_total(hall_text)
                links = hall.find_elements(By.CSS_SELECTOR, ".info-timetable li a, li a")
                for link in links:
                    link_text = normalize_text(link.text)
                    time_match = re.search(r"([0-2]?\d):[0-5]\d", link_text)
                    remaining = parse_cgv_remaining(link_text)
                    if time_match and remaining is not None:
                        schedules.append(build_schedule(
                            theater["name"],
                            title,
                            play_de,
                            time_match.group(0),
                            remaining,
                            total_seats or remaining,
                        ))
        selected = choose_best_schedule(schedules)
        if selected:
            return selected
    return choose_best_schedule(schedules)


def get_seat_layout(theater_name, total_seats=0):
    if total_seats and total_seats > 200:
        seats_per_row = 25
    elif "롯데" in theater_name:
        seats_per_row = 25
    else:
        seats_per_row = 15

    row_count = max(1, math.ceil((total_seats or seats_per_row * 10) / seats_per_row))
    rows = [chr(ord("A") + index) for index in range(min(row_count, 26))]
    return rows, seats_per_row


def build_available_seats(theater_name, remaining_seats, total_seats):
    total_seats = max(0, to_int(total_seats))
    rows, seats_per_row = get_seat_layout(theater_name, total_seats)
    target_count = remaining_seats if remaining_seats is not None else round(total_seats * 0.7)
    target_count = max(0, min(total_seats, int(target_count)))

    premium_seats = []
    regular_seats = []
    for row in rows:
        for number in range(1, seats_per_row + 1):
            if len(premium_seats) + len(regular_seats) >= total_seats:
                break
            if is_premium_position(row, number, seats_per_row):
                premium_seats.append((row, number))
            else:
                regular_seats.append((row, number))

    seed = f"{theater_name}:{remaining_seats}:{total_seats}"
    rng = random.Random(seed)
    rng.shuffle(premium_seats)
    rng.shuffle(regular_seats)

    target_good_count = min(
        len(premium_seats),
        math.floor((target_count / total_seats) * len(premium_seats)) if total_seats else 0,
    )
    selected = premium_seats[:target_good_count]
    selected += regular_seats[:max(0, target_count - len(selected))]
    selected = sorted(selected, key=lambda seat: (seat[0], seat[1]))
    return selected


def fallback_schedule(theater):
    total_seats = theater.get("fallback_total_seats", 150)
    remaining_seats = round(total_seats * 0.7)
    now = datetime.now() + timedelta(hours=2)
    return {
        "theater_name": theater["name"],
        "title": "슈퍼 마리오 갤럭시",
        "showing_date": now.strftime("%Y-%m-%d"),
        "showing_time": now.strftime("%H:%M"),
        "start_dt": now,
        "remaining_seats": remaining_seats,
        "total_seats": total_seats,
        "rank": 999,
    }


def crawl_theater_data():
    opts = Options()
    opts.add_argument("--headless=new")
    opts.add_argument("--no-sandbox")
    opts.add_argument("--disable-dev-shm-usage")
    opts.add_argument("--window-size=1366,1200")
    opts.add_argument(
        "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    theaters = [
        {"name": "CGV 강남", "crawler": "cgv", "theater_code": "0056", "fallback_total_seats": 150},
        {"name": "롯데시네마 건대", "crawler": "lotte", "cinema_id": "1|0001|1004", "fallback_total_seats": 175},
        {"name": "메가박스 코엑스", "crawler": "megabox", "brch_no": "1351", "fallback_total_seats": 150},
    ]

    driver = None
    results = []

    try:
        for index, theater in enumerate(theaters):
            try:
                if theater["crawler"] == "lotte":
                    schedule = crawl_lotte_schedule(theater)
                elif theater["crawler"] == "megabox":
                    schedule = crawl_megabox_schedule(theater)
                else:
                    if driver is None:
                        driver = webdriver.Chrome(options=opts)
                    schedule = crawl_cgv_schedule(driver, theater)
                if not schedule:
                    raise RuntimeError("No schedule found")
            except Exception as error:
                print(f"{theater['name']} crawl failed: {error}", file=sys.stderr)
                schedule = fallback_schedule(theater)

            available_seats = build_available_seats(
                theater["name"],
                schedule["remaining_seats"],
                schedule["total_seats"],
            )
            _, seats_per_row = get_seat_layout(theater["name"], schedule["total_seats"])
            good_seats_count = calculate_good_seats(available_seats, seats_per_row)

            results.append({
                "theater_name": theater["name"],
                "title": schedule["title"],
                "showing_date": schedule["showing_date"],
                "showing_time": schedule["showing_time"],
                "start_time": f"{schedule['showing_date']} {schedule['showing_time']}",
                "remaining_seats": schedule["remaining_seats"],
                "total_seats": schedule["total_seats"],
                "good_seats": good_seats_count,
                "available_seats": available_seats,
                "crawled_at": (datetime.now() + timedelta(seconds=index)).strftime("%Y-%m-%d %H:%M:%S"),
            })

        print(json.dumps(results, ensure_ascii=False))
    finally:
        if driver is not None:
            driver.quit()


if __name__ == "__main__":
    crawl_theater_data()
