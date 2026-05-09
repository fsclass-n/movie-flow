package com.onrender.movieflow.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SeatUtils {
    public static Set<String> computePremiumSeatIds(int totalSeats, int seatsPerRow) {
        int totalRows = (int) Math.ceil(totalSeats / (double) seatsPerRow);
        int premiumSeatCount = Math.max(1, Math.round(totalSeats * 0.2f));

        int rowCount = Math.max(1, Math.round(totalRows * 0.35f));
        if (rowCount > totalRows) {
            rowCount = totalRows;
        }

        int colCount = (int) Math.ceil(premiumSeatCount / (double) rowCount);
        while (colCount > seatsPerRow && rowCount < totalRows) {
            rowCount++;
            colCount = (int) Math.ceil(premiumSeatCount / (double) rowCount);
        }
        if (colCount > seatsPerRow) {
            colCount = seatsPerRow;
        }

        int centerRowIdx = (totalRows - 1) / 2;
        int rowStart = Math.max(0, centerRowIdx - rowCount / 2);
        int rowEnd = Math.min(totalRows - 1, rowStart + rowCount - 1);
        if (rowEnd - rowStart + 1 < rowCount) {
            rowStart = Math.max(0, rowEnd - rowCount + 1);
        }

        int centerCol = (seatsPerRow + 1) / 2;
        int colStart = Math.max(1, centerCol - colCount / 2);
        int colEnd = Math.min(seatsPerRow, colStart + colCount - 1);
        if (colEnd - colStart + 1 < colCount) {
            colStart = Math.max(1, colEnd - colCount + 1);
        }

        Set<String> premiumSeatIds = new HashSet<>();
        for (int rowIdx = rowStart; rowIdx <= rowEnd; rowIdx++) {
            String rowName = String.valueOf((char) ('A' + rowIdx));
            for (int col = colStart; col <= colEnd; col++) {
                premiumSeatIds.add(rowName + col);
            }
        }
        return premiumSeatIds;
    }

    public static int countPremiumAvailableSeats(List<List<?>> availableSeats, Set<String> premiumSeatIds) {
        if (availableSeats == null || premiumSeatIds == null || premiumSeatIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (List<?> seat : availableSeats) {
            if (seat.size() != 2) {
                continue;
            }
            String row = seat.get(0).toString();
            String col = seat.get(1).toString();
            if (premiumSeatIds.contains(row + col)) {
                count++;
            }
        }
        return count;
    }
}
