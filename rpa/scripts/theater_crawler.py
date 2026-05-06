import requests
from bs4 import BeautifulSoup
from seat_logic import calculate_good_seats, get_premium_seats

import html
import io
import json
import math
import random
import re
import sys
from datetime import datetime, timedelta

sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding="utf-8")
sys.stderr = io.TextIOWrapper(sys.stderr.detach(), encoding="utf-8")

MEGABOX_SCHEDULE_URL = "https://www.megabox.co.kr/on/oh/ohc/Brch/schedulePage.do"
LOTTE_TICKETING_URL = "https://www.lottecinema.co.kr/LCWS/Ticketing/TicketingData.aspx"


# ---------------- 공통 유틸 ----------------

def normalize_text(text):
    if not text:
        return ""
    return re.sub(r"\s+", " ", html.unescape(str(text))).strip()


def to_int(value, default=0):
    match = re.search(r"\d+", str(value or ""))
    return int(match.group()) if match else default


def date_candidates(days=3):
    today = datetime.now().date()
    return [today + timedelta(days=i) for i in range(days)]


# ---------------- CGV (경량화) ----------------

def crawl_cgv_schedule(theater):
    schedules = []

    headers = {
        "User-Agent": "Mozilla/5.0",
        "Referer": "https://www.cgv.co.kr/"
    }

    for d in date_candidates():
        date_str = d.strftime("%Y%m%d")
        url = f"https://www.cgv.co.kr/common/showtimes/iframeTheater.aspx?theatercode={theater['theater_code']}&date={date_str}"

        try:
            res = requests.get(url, headers=headers, timeout=10)
            soup = BeautifulSoup(res.text, "lxml")

            for block in soup.select(".col-times"):
                title_tag = block.select_one(".info-movie strong")
                if not title_tag:
                    continue

                title = normalize_text(title_tag.text)

                for hall in block.select(".type-hall"):
                    hall_text = normalize_text(hall.text)

                    total_match = re.search(r"총\s*(\d+)\s*석", hall_text)
                    total = int(total_match.group(1)) if total_match else 0

                    for li in hall.select("li"):
                        text = normalize_text(li.text)

                        time_match = re.search(r"(\d{2}:\d{2})", text)
                        remain_match = re.search(r"(\d+)\s*석", text)

                        if time_match and remain_match:
                            schedules.append({
                                "theater_name": theater["name"],
                                "title": title,
                                "showing_date": d.strftime("%Y-%m-%d"),
                                "showing_time": time_match.group(1),
                                "remaining_seats": int(remain_match.group(1)),
                                "total_seats": total or int(remain_match.group(1))
                            })

        except Exception as e:
            print(f"CGV error: {e}", file=sys.stderr)

    return schedules[0] if schedules else None


# ---------------- 롯데 ----------------

def crawl_lotte_schedule(theater):
    url = LOTTE_TICKETING_URL

    payload = {
        "MethodName": "GetPlaySequence",
        "cinemaID": theater["cinema_id"]
    }

    try:
        res = requests.post(url, data={"paramList": json.dumps(payload)}, timeout=10)
        data = res.json()

        item = data["PlaySeqs"]["Items"][0]

        return {
            "theater_name": theater["name"],
            "title": item["MovieNameKR"],
            "showing_date": item["PlayDt"],
            "showing_time": item["StartTime"],
            "remaining_seats": item["TotalSeatCount"] - item["BookingSeatCount"],
            "total_seats": item["TotalSeatCount"]
        }

    except:
        return None


# ---------------- 메가박스 ----------------

def crawl_megabox_schedule(theater):
    try:
        payload = {
            "brchNo": theater["brch_no"],
            "playDe": datetime.now().strftime("%Y%m%d")
        }

        res = requests.post(MEGABOX_SCHEDULE_URL, json=payload, timeout=10)
        data = res.json()

        item = data["megaMap"]["movieFormList"][0]

        return {
            "theater_name": theater["name"],
            "title": item["movieNm"],
            "showing_date": item["playDe"],
            "showing_time": item["playStartTime"],
            "remaining_seats": item["restSeatCnt"],
            "total_seats": item["totSeatCnt"]
        }

    except:
        return None


# ---------------- 좌석 ----------------

def build_available_seats(total, remain):
    seats = []
    for i in range(remain):
        row = chr(65 + (i // 15))
        col = (i % 15) + 1
        seats.append((row, col))
    return seats


# ---------------- 메인 ----------------

def crawl_theater_data():
    theaters = [
        {"name": "CGV 강남", "crawler": "cgv", "theater_code": "0056"},
        {"name": "롯데시네마 건대", "crawler": "lotte", "cinema_id": "1|0001|1004"},
        {"name": "메가박스 코엑스", "crawler": "megabox", "brch_no": "1351"},
    ]

    results = []

    for theater in theaters:
        try:
            if theater["crawler"] == "cgv":
                data = crawl_cgv_schedule(theater)
            elif theater["crawler"] == "lotte":
                data = crawl_lotte_schedule(theater)
            else:
                data = crawl_megabox_schedule(theater)

            if not data:
                continue

            seats = build_available_seats(
                data["total_seats"],
                data["remaining_seats"]
            )

            premium = get_premium_seats(10, 15)
            good = calculate_good_seats(seats, premium)

            data["good_seats"] = good
            data["available_seats"] = seats

            results.append(data)

        except Exception as e:
            print(f"{theater['name']} error: {e}", file=sys.stderr)

    print(json.dumps(results, ensure_ascii=False))


if __name__ == "__main__":
    crawl_theater_data()