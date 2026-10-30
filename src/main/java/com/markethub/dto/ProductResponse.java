package com.markethub.dto;

import com.markethub.entity.Product;

import java.math.BigDecimal;

public record ProductResponse(Long id, String name, String description, BigDecimal price, int stock,
                              boolean active, Long categoryId, String categoryName,
                              Double averageRating, long reviewCount) {

    public static ProductResponse from(Product product, ReviewStats stats) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getStock(), product.isActive(),
                product.getCategory().getId(), product.getCategory().getName(),
                stats.averageRating(), stats.reviewCount());
    }
}
