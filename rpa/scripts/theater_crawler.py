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

sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding="utf-8")
sys.stderr = io.TextIOWrapper(sys.stderr.detach(), encoding="utf-8")


MEGABOX_SCHEDULE_URL = "https://www.megabox.co.kr/on/oh/ohc/Brch/schedulePage.do"
LOTTE_TICKETING_URL = "https://www.lottecinema.co.kr/LCWS/Ticketing/TicketingData.aspx"
REQUEST_TIMEOUT = 15

CGV_SESSION = requests.Session()
CGV_SESSION.headers.update({
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
})


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


def absolutize_url(url, base_url):
    if not url:
        return ""
    url = str(url).strip()
    if url.startswith("//"):
        return "https:" + url
    if url.startswith("http://") or url.startswith("https://"):
        return url
    return urllib.parse.urljoin(base_url, url)


def first_image_from_value(value):
    if not value:
        return ""
    if isinstance(value, str) and re.search(r"\.(jpg|jpeg|png|webp)(\?|$)", value, re.IGNORECASE):
        return value
    if isinstance(value, dict):
        for key in ("posterUrl", "posterURL", "PosterURL", "movieImg", "imgPath", "imgPathNm", "posterImgPath", "FilePath"):
            found = first_image_from_value(value.get(key))
            if found:
                return found
        for nested in value.values():
            found = first_image_from_value(nested)
            if found:
                return found
    if isinstance(value, list):
        for nested in value:
            found = first_image_from_value(nested)
            if found:
                return found
    return ""


def post_json(url, payload, referer):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {
        "Content-Type": "application/json;charset=UTF-8",
        "X-Requested-With": "XMLHttpRequest",
        "Accept": "application/json, text/javascript, */*; q=0.01",
        "Referer": referer,
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        ),
    }
    request = urllib.request.Request(url, data=body, headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT) as response:
        return json.loads(response.read().decode("utf-8"))


def post_lotte(payload):
    headers = {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "X-Requested-With": "XMLHttpRequest",
        "Accept": "application/json, text/javascript, */*; q=0.01",
        "Referer": "https://www.lottecinema.co.kr/NLCHS/Ticketing",
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        ),
    }
    body = urllib.parse.urlencode({
        "paramList": json.dumps(payload, ensure_ascii=False)
    }).encode("utf-8")
    request = urllib.request.Request(LOTTE_TICKETING_URL, data=body, headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT) as response:
        return json.loads(response.read().decode("utf-8-sig"))


def cgv_api_get(path, params):
    query = urllib.parse.urlencode(params)
    url = f"{CGV_API_BASE_URL}{path}?{query}" if query else f"{CGV_API_BASE_URL}{path}"
    timestamp = str(int(time.time()))
    raw_signature = f"{timestamp}|{path}|".encode("utf-8")
    signature = base64.b64encode(
        hmac.new(CGV_API_SECRET.encode("utf-8"), raw_signature, hashlib.sha256).digest()
    ).decode("utf-8")
    headers = {
        "Accept": "application/json",
        "Accept-Language": "ko-KR",
        "Origin": "https://cgv.co.kr",
        "Referer": "https://cgv.co.kr/",
        "X-TIMESTAMP": timestamp,
        "X-SIGNATURE": signature,
    }
    response = SESSION.get(url, headers=headers, timeout=8)
    response.raise_for_status()
    return response.json()


def choose_best_schedule(schedules):
    if not schedules:
        return None

    now = datetime.now() - timedelta(minutes=20)
    future = [schedule for schedule in schedules if schedule["start_dt"] >= now]
    candidates = future or schedules
    candidates = [schedule for schedule in candidates if schedule["remaining_seats"] > 0] or candidates

    morning_schedules = [schedule for schedule in candidates if schedule["start_dt"].hour < 12]
    if morning_schedules:
        candidates = morning_schedules

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


def build_schedule(theater_name, title, date_value, time_value, remaining_seats, total_seats, rank=999, image_url=""):
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
        "image_url": image_url,
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
        data = post_json(MEGABOX_SCHEDULE_URL, payload, "https://www.megabox.co.kr/booking/timetable")
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
                    absolutize_url(first_image_from_value(item), "https://www.megabox.co.kr/"),
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
                    absolutize_url(first_image_from_value(item), "https://www.lottecinema.co.kr/"),
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


def iter_nested_values(value):
    if isinstance(value, dict):
        yield value
        for nested in value.values():
            yield from iter_nested_values(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from iter_nested_values(nested)


def first_value(item, keys, default=""):
    for key in keys:
        value = item.get(key)
        if value not in (None, ""):
            return value
    return default


def parse_cgv_api_schedules(theater, play_de, data):
    schedules = []
    for item in iter_nested_values(data):
        if not isinstance(item, dict):
            continue
        title = first_value(item, ["movNm", "movieNm", "prodNm", "expoProdNm", "movEngNm"])
        date_value = first_value(item, ["scnYmd", "playYmd", "scnsYmd", "scnDt"], play_de)
        time_value = first_value(item, ["scnsrtTm", "scnStrtTm", "playStartTime", "startTime"])
        if not title or not time_value:
            continue

        time_value = str(time_value)
        if re.fullmatch(r"\d{4}", time_value):
            time_value = f"{time_value[:2]}:{time_value[2:]}"
        total_seats = to_int(first_value(item, ["maxcnt", "totSeatCnt", "totalSeatCount", "seatCnt", "scnsSeatCnt"]))
        sold_seats = to_int(first_value(item, ["atktcnt", "bookingSeatCount", "bookedSeatCnt"], 0))
        remaining = first_value(item, ["restSeatCnt", "remainingSeatCount", "seatRemainCnt"], None)
        remaining_seats = to_int(remaining, max(0, total_seats - sold_seats) if total_seats else 0)
        if total_seats <= 0 and remaining_seats <= 0:
            continue

        schedules.append(build_schedule(
            theater["name"],
            title,
            date_value,
            time_value,
            remaining_seats,
            total_seats or remaining_seats,
            image_url=absolutize_url(first_image_from_value(item), "https://www.cgv.co.kr/"),
        ))
    return schedules


def crawl_cgv_api_schedule(theater):
    schedules = []
    paths = [
        "/cnm/site/searchSscnsScnList",
        "/cnm/sscns/searchSscnsList",
    ]
    co_codes = ["A420", "01", "CGV"]
    for play_date in date_candidates():
        play_de = play_date.strftime("%Y%m%d")
        for path in paths:
            for co_cd in co_codes:
                params_candidates = [
                    {"coCd": co_cd, "siteNo": theater["theater_code"], "scnYmd": play_de},
                    {"coCd": co_cd, "siteNo": theater["theater_code"], "playYmd": play_de},
                    {"coCd": co_cd, "siteNo": theater["theater_code"], "playYmd": play_de, "scnYmd": play_de},
                    {"coCd": co_cd, "siteNo": theater["theater_code"]},
                    {"coCd": co_cd, "theaterCode": theater["theater_code"], "scnYmd": play_de},
                    {"coCd": co_cd, "theaterCode": theater["theater_code"], "playYmd": play_de},
                    {"coCd": co_cd, "theaterCode": theater["theater_code"], "playYmd": play_de, "scnYmd": play_de},
                ]
                for params in params_candidates:
                    try:
                        data = cgv_api_get(path, params)
                        schedules.extend(parse_cgv_api_schedules(theater, play_de, data))
                    except Exception:
                        continue
                    selected = choose_best_schedule(schedules)
                    if selected:
                        return selected
    return choose_best_schedule(schedules)


def find_child_text_soup(element, selectors):
    for selector in selectors:
        for child in element.select(selector):
            text = normalize_text(child.get_text() or child.get("title") or child.get("alt"))
            if is_valid_title(text):
                return clean_movie_title(text)
    return None


def extract_url_from_style(style_value):
    if not style_value:
        return ""
    match = re.search(r"url\(['\"]?(.*?)['\"]?\)", style_value)
    return match.group(1) if match else ""


def find_image_soup(element, base_url):
    def scan_element(elem):
        for image in elem.select("img"):
            src = image.get("src") or image.get("data-src") or image.get("data-original") or image.get("data-lazy") or ""
            if not src and image.has_attr("style"):
                src = extract_url_from_style(image["style"])
            if src and re.search(r"\.(jpg|jpeg|png|webp)(\?|$)", src, re.IGNORECASE):
                return absolutize_url(src, base_url)
        if elem.has_attr("style"):
            src = extract_url_from_style(elem["style"])
            if src and re.search(r"\.(jpg|jpeg|png|webp)(\?|$)", src, re.IGNORECASE):
                return absolutize_url(src, base_url)
        return ""

    result = scan_element(element)
    if result:
        return result

    parent = element
    for _ in range(5):
        parent = parent.parent
        if not parent:
            break
        result = scan_element(parent)
        if result:
            return result

    for image in element.find_all("img"):
        src = image.get("src") or image.get("data-src") or image.get("data-original") or image.get("data-lazy") or ""
        if src and re.search(r"\.(jpg|jpeg|png|webp)(\?|$)", src, re.IGNORECASE):
            return absolutize_url(src, base_url)

    return ""


def fetch_html(url, referer="https://www.cgv.co.kr/"):
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        ),
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        "Referer": referer,
    }
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT) as response:
        return response.read().decode("utf-8")


def create_chrome_driver():
    try:
        from selenium import webdriver
        from selenium.webdriver.chrome.options import Options
        from selenium.webdriver.chrome.service import Service
    except Exception as error:
        raise RuntimeError(f"Selenium import failed: {error}") from error

    options = Options()
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    options.add_argument("--window-size=1280,1600")
    options.add_argument("--lang=ko-KR")

    service = Service()
    return webdriver.Chrome(service=service, options=options)


def fetch_html_with_selenium(url):
    driver = create_chrome_driver()
    try:
        driver.set_page_load_timeout(REQUEST_TIMEOUT)
        driver.get(url)
        time.sleep(2)
        return driver.page_source
    finally:
        driver.quit()


def fetch_cgv_html_with_selenium(theater_code, play_de):
    try:
        from selenium.webdriver.common.by import By
        from selenium.webdriver.support import expected_conditions as ec
        from selenium.webdriver.support.ui import WebDriverWait
    except Exception as error:
        raise RuntimeError(f"Selenium wait import failed: {error}") from error

    url = f"https://www.cgv.co.kr/theaters/?areacode=01&theaterCode={theater_code}&date={play_de}"
    driver = create_chrome_driver()
    try:
        driver.set_page_load_timeout(REQUEST_TIMEOUT)
        driver.get(url)
        wait = WebDriverWait(driver, REQUEST_TIMEOUT)
        wait.until(ec.frame_to_be_available_and_switch_to_it((By.ID, "ifrm_movie_time_table")))
        time.sleep(1)
        return driver.page_source
    finally:
        driver.quit()


def parse_cgv_html(theater, play_de, html_text):
    schedules = []
    soup = BeautifulSoup(html_text, "html.parser")
    for block in soup.select(
        ".col-times, .sect-showtimes, .sect-showtime, .sect-timetable, .sect-film, .movie-item, .movie",
    ):
        title = find_child_text_soup(block, [
            ".info-movie a strong",
            ".info-movie strong",
            "strong.title",
            ".title",
            ".movie-title",
            ".tit-movie",
        ])
        if not title:
            continue
        image_url = find_image_soup(block, "https://www.cgv.co.kr/")
        halls = block.select(".type-hall") or [block]
        for hall in halls:
            hall_text = normalize_text(hall.get_text())
            total_seats = parse_cgv_total(hall_text)
            links = hall.select(".info-timetable li a, li a")
            for link in links:
                link_text = normalize_text(link.get_text())
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
                        image_url=image_url,
                    ))
    return schedules


def fetch_cgv_with_requests(theater_code, play_de):
    # CGV 메인 극장 페이지 먼저 요청 (쿠키/세션 설정)
    main_url = f"https://www.cgv.co.kr/theaters/?areacode=01&theaterCode={theater_code}&date={play_de}"
    CGV_SESSION.get(main_url, timeout=REQUEST_TIMEOUT)

    # iframe 페이지 요청 (Referer 필수)
    iframe_url = f"https://www.cgv.co.kr/common/showtimes/iframeTheater.aspx?theatercode={theater_code}&date={play_de}"
    response = CGV_SESSION.get(
        iframe_url,
        timeout=REQUEST_TIMEOUT,
        headers={"Referer": f"https://www.cgv.co.kr/theaters/?areacode=01&theaterCode={theater_code}"},
    )
    response.raise_for_status()
    return response.text


def crawl_cgv_schedule(theater):
    import traceback

    schedules = []
    for play_date in date_candidates():
        play_de = play_date.strftime("%Y%m%d")
        try:
            html_text = fetch_cgv_with_requests(theater["theater_code"], play_de)
            parsed = parse_cgv_html(theater, play_de, html_text)
            if parsed:
                schedules.extend(parsed)
                print(f"CGV parsed {len(parsed)} schedules", file=sys.stderr)
            else:
                print(f"{theater['name']} requests empty, trying selenium fallback...", file=sys.stderr)
                html_text = fetch_cgv_html_with_selenium(theater["theater_code"], play_de)
                schedules.extend(parse_cgv_html(theater, play_de, html_text))
        except Exception as error:
            print(f"{theater['name']} crawl failed: {error}", file=sys.stderr)
            traceback.print_exc(file=sys.stderr)
            continue

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
    return sorted(selected, key=lambda seat: (seat[0], seat[1]))


def fallback_schedule(theater):
    total_seats = theater.get("fallback_total_seats", 150)
    remaining_seats = round(total_seats * 0.7)
    now = datetime.now() + timedelta(hours=2)
    return {
        "theater_name": theater["name"],
        "title": theater.get("fallback_title", "상영 정보 확인 중"),
        "showing_date": now.strftime("%Y-%m-%d"),
        "showing_time": now.strftime("%H:%M"),
        "start_dt": now,
        "remaining_seats": remaining_seats,
        "total_seats": total_seats,
        "rank": 999,
        "image_url": "",
    }


def crawl_theater_data():
    theaters = [
        {"name": "롯데시네마 건대", "crawler": "lotte", "cinema_id": "1|0001|1004", "fallback_total_seats": 175},
        {"name": "메가박스 코엑스", "crawler": "megabox", "brch_no": "1351", "fallback_total_seats": 150},
        {"name": "CGV 강남", "crawler": "cgv", "theater_code": "0056", "fallback_total_seats": 150},
    ]

    results = []
    for index, theater in enumerate(theaters):
        try:
            if theater["crawler"] == "lotte":
                schedule = crawl_lotte_schedule(theater)
            elif theater["crawler"] == "megabox":
                schedule = crawl_megabox_schedule(theater)
            else:
                schedule = crawl_cgv_schedule(theater)
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
        rows, seats_per_row = get_seat_layout(theater["name"], schedule["total_seats"])
        premium_seats = get_premium_seats(len(rows), seats_per_row)
        good_seats_count = calculate_good_seats(available_seats, premium_seats)

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
            "image_url": schedule.get("image_url", ""),
            "crawled_at": (datetime.now() + timedelta(seconds=index)).strftime("%Y-%m-%d %H:%M:%S"),
        })

    print(json.dumps(results, ensure_ascii=False))


if __name__ == "__main__":
    crawl_theater_data()
