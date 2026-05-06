def get_premium_seats(total_rows, seats_per_row):
    """
    전체 좌석의 20%를 명당으로 선정 (center 기준)

    Args:
        total_rows: 총 행 수 (e.g., 8 for rows A-H)
        seats_per_row: 행당 좌석 수 (e.g., 15 or 25)

    Returns:
        set of (row_letter, seat_number) tuples - 명당 좌석 집합
    """
    total_seats = total_rows * seats_per_row
    premium_seat_count = max(1, round(total_seats * 0.2))

    row_count = max(1, round(total_rows * 0.35))
    if row_count > total_rows:
        row_count = total_rows

    col_count = -(-premium_seat_count // row_count)
    while col_count > seats_per_row and row_count < total_rows:
        row_count += 1
        col_count = -(-premium_seat_count // row_count)
    if col_count > seats_per_row:
        col_count = seats_per_row

    center_row_idx = (total_rows - 1) // 2
    row_start = max(0, center_row_idx - row_count // 2)
    row_end = min(total_rows - 1, row_start + row_count - 1)
    if row_end - row_start + 1 < row_count:
        row_start = max(0, row_end - row_count + 1)

    center_col = (seats_per_row + 1) / 2
    col_start = max(1, int(center_col - col_count / 2))
    col_end = min(seats_per_row, col_start + col_count - 1)
    if col_end - col_start + 1 < col_count:
        col_start = max(1, col_end - col_count + 1)

    premium_seats = set()
    for row_idx in range(row_start, row_end + 1):
        row_letter = chr(ord('A') + row_idx)
        for col in range(col_start, col_end + 1):
            premium_seats.add((row_letter, col))

    return premium_seats


def is_premium_seat(row, seat_number, premium_seats):
    """
    특정 좌석이 명당인지 확인
    
    Args:
        row: 행 (e.g., 'A', 'B', 'C')
        seat_number: 좌석 번호 (e.g., 1~15)
        premium_seats: 명당 좌석 집합 (set of tuples)
    
    Returns:
        bool - 명당 여부
    """
    return (row, seat_number) in premium_seats


def calculate_good_seats(seat_list, premium_seats):
    """
    예약된 좌석 중 명당 개수 계산
    
    Args:
        seat_list: [(행, 번호), (행, 번호)...] 형태의 튜플 리스트
        premium_seats: 명당 좌석 집합 (set of tuples)
    
    Returns:
        int - 명당 좌석 개수
    """
    count = 0
    for row, num in seat_list:
        if is_premium_seat(row, num, premium_seats):
            count += 1
    return count
