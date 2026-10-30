package com.markethub.repository;

import com.markethub.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findAllByProductId(Long productId, Pageable pageable);

    boolean existsByProductIdAndAuthorId(Long productId, Long authorId);

    /**
     * Средняя оценка и число отзывов сразу для набора товаров — одним запросом,
     * чтобы не получить N+1 при выводе страницы каталога.
     * Каждая строка: [productId (Long), avgRating (Double), count (Long)].
     */
    @Query("""
            select r.product.id, avg(r.rating), count(r)
            from Review r
            where r.product.id in :productIds
            group by r.product.id
            """)
    List<Object[]> statsForProducts(@Param("productIds") Collection<Long> productIds);
}
