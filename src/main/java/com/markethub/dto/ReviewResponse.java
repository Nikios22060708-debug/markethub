package com.markethub.dto;

import com.markethub.entity.Review;

import java.time.Instant;

public record ReviewResponse(Long id, Long productId, Long authorId, String authorUsername, int rating,
                             String comment, boolean verifiedPurchase, Instant createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getProduct().getId(), review.getAuthor().getId(),
                review.getAuthor().getUsername(), review.getRating(), review.getComment(),
                review.isVerifiedPurchase(), review.getCreatedAt());
    }
}
