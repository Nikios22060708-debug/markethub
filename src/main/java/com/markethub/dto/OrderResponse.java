package com.markethub.dto;

import com.markethub.entity.Order;
import com.markethub.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, Long userId, String username, Long addressId, OrderStatus status,
                            BigDecimal totalAmount, List<OrderItemResponse> items, Long paymentId,
                            Instant createdAt, Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getUser().getId(), order.getUser().getUsername(),
                order.getAddress().getId(), order.getStatus(), order.getTotalAmount(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getPayment() == null ? null : order.getPayment().getId(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
