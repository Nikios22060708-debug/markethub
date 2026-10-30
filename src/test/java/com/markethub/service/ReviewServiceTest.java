package com.markethub.service;

import com.markethub.dto.ReviewRequest;
import com.markethub.dto.ReviewResponse;
import com.markethub.entity.OrderStatus;
import com.markethub.entity.Product;
import com.markethub.entity.Review;
import com.markethub.entity.Role;
import com.markethub.entity.User;
import com.markethub.exception.ConflictException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.OrderItemRepository;
import com.markethub.repository.ReviewRepository;
import com.markethub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static com.markethub.TestData.auth;
import static com.markethub.TestData.category;
import static com.markethub.TestData.product;
import static com.markethub.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductService productService;
    @InjectMocks
    private ReviewService reviewService;

    private User alice;
    private Product phone;

    @BeforeEach
    void setUp() {
        alice = user(10L, "alice", Role.CUSTOMER);
        phone = product(7L, "Phone", "100", 5, category(1L, "C"));
    }

    @Test
    void createMarksVerifiedPurchase() {
        when(productService.getEntity(7L)).thenReturn(phone);
        when(reviewRepository.existsByProductIdAndAuthorId(7L, 10L)).thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(alice));
        when(orderItemRepository.hasPurchased(10L, 7L, OrderStatus.CANCELLED)).thenReturn(true);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = reviewService.create(auth(10L, "alice", Role.CUSTOMER), 7L,
                new ReviewRequest(5, "Отлично"));

        assertThat(response.verifiedPurchase()).isTrue();
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.authorUsername()).isEqualTo("alice");
    }

    @Test
    void secondReviewOnSameProductIsRejected() {
        when(productService.getEntity(7L)).thenReturn(phone);
        when(reviewRepository.existsByProductIdAndAuthorId(7L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create(auth(10L, "alice", Role.CUSTOMER), 7L,
                new ReviewRequest(4, null))).isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyAuthorCanEdit() {
        Review review = new Review(phone, alice, 3, "так себе", false);
        review.setId(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThat(reviewService.update(auth(10L, "alice", Role.CUSTOMER), 1L, new ReviewRequest(4, "лучше"))
                .rating()).isEqualTo(4);
        assertThatThrownBy(() -> reviewService.update(auth(1L, "admin", Role.ADMIN), 1L, new ReviewRequest(1, "")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void authorOrStaffCanDelete() {
        Review review = new Review(phone, alice, 3, null, false);
        review.setId(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.delete(auth(11L, "bob", Role.CUSTOMER), 1L))
                .isInstanceOf(AccessDeniedException.class);

        reviewService.delete(auth(2L, "manager", Role.MANAGER), 1L);
        verify(reviewRepository).delete(review);
    }

    @Test
    void listForUnknownProductFails() {
        when(productService.getEntity(99L)).thenThrow(new NotFoundException("нет"));

        assertThatThrownBy(() -> reviewService.listByProduct(99L, PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getUnknownReviewFails() {
        when(reviewRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.get(5L)).isInstanceOf(NotFoundException.class);
    }
}
