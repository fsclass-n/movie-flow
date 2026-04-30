def is_premium_seat(row, seat_number):
    # 명당 조건: C~E행의 5~10번 좌석
    prime_rows = ["C", "D", "E"]
    prime_seats = range(5, 11)
    return row in prime_rows and seat_number in prime_seats

def calculate_good_seats(seat_list):
    """
    seat_list: [(행, 번호), (행, 번호)...] 형태의 튜플 리스트
    """
    count = 0
    for row, num in seat_list:
        if is_premium_seat(row, num):
            count += 1
    return count