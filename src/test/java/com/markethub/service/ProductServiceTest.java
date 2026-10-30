package com.markethub.service;

import com.markethub.dto.ProductFilter;
import com.markethub.dto.ProductRequest;
import com.markethub.dto.ProductResponse;
import com.markethub.entity.Category;
import com.markethub.entity.Product;
import com.markethub.exception.BadRequestException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.OrderItemRepository;
import com.markethub.repository.ProductRepository;
import com.markethub.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.markethub.TestData.category;
import static com.markethub.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private CategoryService categoryService;
    @InjectMocks
    private ProductService productService;

    private Category phones;
    private Product phone;

    @BeforeEach
    void setUp() {
        phones = category(1L, "Смартфоны");
        phone = product(7L, "Phone", "100.00", 5, phones);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAddsReviewStatsToEachProduct() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(phone), pageable, 1));
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{7L, 4.3333, 3L});
        when(reviewRepository.statsForProducts(anyCollection())).thenReturn(rows);

        var page = productService.search(new ProductFilter(1L, "pho", null, null), pageable);

        assertThat(page.content()).singleElement().satisfies(p -> {
            assertThat(p.averageRating()).isEqualTo(4.33);
            assertThat(p.reviewCount()).isEqualTo(3);
            assertThat(p.categoryName()).isEqualTo("Смартфоны");
        });
    }

    @Test
    void searchRejectsInvertedPriceRange() {
        ProductFilter filter = new ProductFilter(null, null, new BigDecimal("500"), new BigDecimal("100"));

        assertThatThrownBy(() -> productService.search(filter, PageRequest.of(0, 10)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(productRepository);
    }

    @Test
    void getWithoutReviewsHasEmptyStats() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(phone));
        when(reviewRepository.statsForProducts(anyCollection())).thenReturn(List.of());

        ProductResponse response = productService.get(7L);

        assertThat(response.averageRating()).isNull();
        assertThat(response.reviewCount()).isZero();
    }

    @Test
    void createAndUpdateMoveProductBetweenCategories() {
        Category accessories = category(2L, "Аксессуары");
        when(categoryService.getEntity(1L)).thenReturn(phones);
        when(categoryService.getEntity(2L)).thenReturn(accessories);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });
        when(productRepository.findById(8L)).thenAnswer(invocation -> Optional.of(phone));
        when(reviewRepository.statsForProducts(anyCollection())).thenReturn(List.of());

        ProductResponse created = productService.create(
                new ProductRequest("Case", "чехол", new BigDecimal("9.99"), 3, 1L, false));
        assertThat(created.active()).isFalse();

        ProductResponse updated = productService.update(8L,
                new ProductRequest("Phone 2", null, new BigDecimal("120.00"), 4, 2L, true));
        assertThat(updated.categoryName()).isEqualTo("Аксессуары");
        assertThat(updated.price()).isEqualByComparingTo("120.00");
        assertThat(updated.stock()).isEqualTo(4);
    }

    @Test
    void deleteDeactivatesProductThatWasOrdered() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(phone));
        when(orderItemRepository.existsByProductId(7L)).thenReturn(true);

        assertThat(productService.delete(7L)).isFalse();
        assertThat(phone.isActive()).isFalse();
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void deleteRemovesProductWithoutOrders() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(phone));
        when(orderItemRepository.existsByProductId(7L)).thenReturn(false);

        assertThat(productService.delete(7L)).isTrue();
        verify(productRepository).delete(phone);
    }

    @Test
    void unknownProductThrowsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get(99L)).isInstanceOf(NotFoundException.class);
    }
}
