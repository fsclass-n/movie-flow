def is_premium_seat(row, seat_number):
    # 예: 가운데 줄, 중간 좌석이 “명당”이라고 가정
    prime_rows = ["B", "C", "D", "E"]
    prime_seats = range(5, 10)
    return row in prime_rows and seat_number in prime_seats

if __name__ == "__main__":
    # 테스트용
    print("명당 자리 체크 예시:", is_premium_seat("C", 7))