def premium_seat_range(seats_per_row):
    center = (seats_per_row + 1) / 2
    start = max(1, round(center - 2))
    end = min(seats_per_row, round(center + 3))
    return range(start, end + 1)


def is_premium_seat(row, seat_number, seats_per_row=15):
    # 명당 조건: C~E행의 스크린 중앙 좌석
    prime_rows = ["C", "D", "E"]
    prime_seats = premium_seat_range(seats_per_row)
    return row in prime_rows and seat_number in prime_seats


def calculate_good_seats(seat_list, seats_per_row=15):
    """
    seat_list: [(행, 번호), (행, 번호)...] 형태의 튜플 리스트
    """
    count = 0
    for row, num in seat_list:
        if is_premium_seat(row, num, seats_per_row):
            count += 1
    return count
