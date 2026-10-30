package com.markethub.repository;

import com.markethub.entity.OrderItem;
import com.markethub.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByProductId(Long productId);

    /** Покупал ли пользователь товар (в заказе, который не был отменён). */
    @Query("""
            select case when count(oi) > 0 then true else false end
            from OrderItem oi
            where oi.order.user.id = :userId
              and oi.product.id = :productId
              and oi.order.status <> :excludedStatus
            """)
    boolean hasPurchased(@Param("userId") Long userId,
                         @Param("productId") Long productId,
                         @Param("excludedStatus") OrderStatus excludedStatus);
}
