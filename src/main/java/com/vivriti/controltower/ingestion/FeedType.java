package com.vivriti.controltower.ingestion;

public enum FeedType {
    ORIGINATOR(9, 0, 4, 3, 7, 8),
    LMS(8, 0, 4, 3, 7, 3),
    BANK(7, 0, 3, 2, 6, 2);

    private final int columnCount;
    private final int identifierColumn;
    private final int amountColumn;
    private final int timestampColumn;
    private final int batchColumn;
    private final int arrivalTimestampColumn;

    FeedType(int columnCount, int identifierColumn, int amountColumn, int timestampColumn, int batchColumn, int arrivalTimestampColumn) {
        this.columnCount = columnCount;
        this.identifierColumn = identifierColumn;
        this.amountColumn = amountColumn;
        this.timestampColumn = timestampColumn;
        this.batchColumn = batchColumn;
        this.arrivalTimestampColumn = arrivalTimestampColumn;
    }

    public int columnCount() {
        return columnCount;
    }

    public int identifierColumn() {
        return identifierColumn;
    }

    public int amountColumn() {
        return amountColumn;
    }

    public int timestampColumn() {
        return timestampColumn;
    }

    public int batchColumn() {
        return batchColumn;
    }

    public int arrivalTimestampColumn() {
        return arrivalTimestampColumn;
    }
}