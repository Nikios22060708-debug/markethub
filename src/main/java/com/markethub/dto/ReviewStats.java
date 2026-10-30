package com.markethub.dto;

/** Агрегат по отзывам товара. */
public record ReviewStats(Double averageRating, long reviewCount) {

    public static final ReviewStats EMPTY = new ReviewStats(null, 0);

    public static ReviewStats of(double average, long count) {
        return new ReviewStats(Math.round(average * 100.0) / 100.0, count);
    }
}
