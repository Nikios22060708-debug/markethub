package com.markethub.service;

import com.markethub.dto.PageResponse;
import com.markethub.dto.ReviewRequest;
import com.markethub.dto.ReviewResponse;
import com.markethub.entity.OrderStatus;
import com.markethub.entity.Product;
import com.markethub.entity.Review;
import com.markethub.entity.User;
import com.markethub.exception.ConflictException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.OrderItemRepository;
import com.markethub.repository.ReviewRepository;
import com.markethub.repository.UserRepository;
import com.markethub.security.AuthUser;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отзывы. Один отзыв на товар от пользователя; отзыв покупателя помечается как «подтверждённая покупка».
 * Редактирует только автор, удаляет автор или сотрудник (модерация).
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    public ReviewService(ReviewRepository reviewRepository, OrderItemRepository orderItemRepository,
                         UserRepository userRepository, ProductService productService) {
        this.reviewRepository = reviewRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
    }

    public PageResponse<ReviewResponse> listByProduct(Long productId, Pageable pageable) {
        productService.getEntity(productId);
        return PageResponse.from(reviewRepository.findAllByProductId(productId, pageable).map(ReviewResponse::from));
    }

    public ReviewResponse get(Long id) {
        return ReviewResponse.from(getEntity(id));
    }

    @Transactional
    public ReviewResponse create(AuthUser authUser, Long productId, ReviewRequest request) {
        Product product = productService.getEntity(productId);
        if (reviewRepository.existsByProductIdAndAuthorId(productId, authUser.id())) {
            throw new ConflictException("Вы уже оставили отзыв на этот товар");
        }
        User author = userRepository.findById(authUser.id())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        boolean verified = orderItemRepository.hasPurchased(authUser.id(), productId, OrderStatus.CANCELLED);
        Review review = new Review(product, author, request.rating(), request.comment(), verified);
        return ReviewResponse.from(reviewRepository.save(review));
    }

    @Transactional
    public ReviewResponse update(AuthUser authUser, Long id, ReviewRequest request) {
        Review review = getEntity(id);
        if (!review.getAuthor().getId().equals(authUser.id())) {
            throw new AccessDeniedException("Редактировать можно только свой отзыв");
        }
        review.setRating(request.rating());
        review.setComment(request.comment());
        return ReviewResponse.from(review);
    }

    @Transactional
    public void delete(AuthUser authUser, Long id) {
        Review review = getEntity(id);
        if (!authUser.isStaff() && !review.getAuthor().getId().equals(authUser.id())) {
            throw new AccessDeniedException("Удалить можно только свой отзыв");
        }
        reviewRepository.delete(review);
    }

    private Review getEntity(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Отзыв " + id + " не найден"));
    }
}
