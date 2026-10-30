package com.markethub.web;

import com.markethub.dto.PageResponse;
import com.markethub.dto.ReviewRequest;
import com.markethub.dto.ReviewResponse;
import com.markethub.security.AuthUser;
import com.markethub.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/products/{productId}/reviews")
    public PageResponse<ReviewResponse> listByProduct(@PathVariable Long productId,
                                                      @PageableDefault(size = 20, sort = "createdAt",
                                                              direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.listByProduct(productId, pageable);
    }

    @GetMapping("/api/reviews/{id}")
    public ReviewResponse get(@PathVariable Long id) {
        return reviewService.get(id);
    }

    @PostMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ReviewResponse> create(@PathVariable Long productId,
                                                 @Valid @RequestBody ReviewRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        ReviewResponse created = reviewService.create(AuthUser.from(jwt), productId, request);
        return ResponseEntity.created(URI.create("/api/reviews/" + created.id())).body(created);
    }

    @PutMapping("/api/reviews/{id}")
    public ReviewResponse update(@PathVariable Long id, @Valid @RequestBody ReviewRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return reviewService.update(AuthUser.from(jwt), id, request);
    }

    @DeleteMapping("/api/reviews/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        reviewService.delete(AuthUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
