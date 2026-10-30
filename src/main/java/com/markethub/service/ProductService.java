package com.markethub.service;

import com.markethub.dto.PageResponse;
import com.markethub.dto.ProductFilter;
import com.markethub.dto.ProductRequest;
import com.markethub.dto.ProductResponse;
import com.markethub.dto.ReviewStats;
import com.markethub.entity.Category;
import com.markethub.entity.Product;
import com.markethub.exception.BadRequestException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.OrderItemRepository;
import com.markethub.repository.ProductRepository;
import com.markethub.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final CategoryService categoryService;

    public ProductService(ProductRepository productRepository, ReviewRepository reviewRepository,
                          OrderItemRepository orderItemRepository, CategoryService categoryService) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.orderItemRepository = orderItemRepository;
        this.categoryService = categoryService;
    }

    /** Каталог: только активные товары, фильтры + пагинация + сортировка. */
    public PageResponse<ProductResponse> search(ProductFilter filter, Pageable pageable) {
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new BadRequestException("minPrice не может быть больше maxPrice");
        }
        Page<Product> page = productRepository.findAll(ProductSpecifications.matching(filter, true), pageable);
        Map<Long, ReviewStats> stats = loadStats(page.getContent().stream().map(Product::getId).toList());
        return PageResponse.from(page.map(p -> ProductResponse.from(p, stats.getOrDefault(p.getId(), ReviewStats.EMPTY))));
    }

    public ProductResponse get(Long id) {
        Product product = getEntity(id);
        return ProductResponse.from(product, loadStats(List.of(id)).getOrDefault(id, ReviewStats.EMPTY));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = categoryService.getEntity(request.categoryId());
        Product product = new Product(request.name(), request.description(), request.price(), request.stock(), category);
        if (request.active() != null) {
            product.setActive(request.active());
        }
        Product saved = productRepository.save(product);
        log.info("Создан товар #{} '{}'", saved.getId(), saved.getName());
        return ProductResponse.from(saved, ReviewStats.EMPTY);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getEntity(id);
        if (!product.getCategory().getId().equals(request.categoryId())) {
            product.setCategory(categoryService.getEntity(request.categoryId()));
        }
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        if (request.active() != null) {
            product.setActive(request.active());
        }
        return ProductResponse.from(product, loadStats(List.of(id)).getOrDefault(id, ReviewStats.EMPTY));
    }

    /**
     * Если товар уже фигурирует в заказах, физически удалять его нельзя (испортится история заказов),
     * поэтому он снимается с продажи. Иначе — удаляется вместе с отзывами.
     *
     * @return true, если товар удалён; false, если только деактивирован
     */
    @Transactional
    public boolean delete(Long id) {
        Product product = getEntity(id);
        if (orderItemRepository.existsByProductId(id)) {
            product.setActive(false);
            log.info("Товар #{} есть в заказах — снят с продажи вместо удаления", id);
            return false;
        }
        productRepository.delete(product);
        return true;
    }

    Product getEntity(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Товар " + id + " не найден"));
    }

    private Map<Long, ReviewStats> loadStats(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ReviewStats> result = new HashMap<>();
        for (Object[] row : reviewRepository.statsForProducts(productIds)) {
            Long productId = ((Number) row[0]).longValue();
            double average = ((Number) row[1]).doubleValue();
            long count = ((Number) row[2]).longValue();
            result.put(productId, ReviewStats.of(average, count));
        }
        return result;
    }
}
