package com.markethub.dto;

import java.math.BigDecimal;

/** Параметры поиска по каталогу; любое поле может быть null. */
public record ProductFilter(Long categoryId, String q, BigDecimal minPrice, BigDecimal maxPrice) {

    public static ProductFilter empty() {
        return new ProductFilter(null, null, null, null);
    }
}
