package com.markethub.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.markethub.TestData.category;
import static com.markethub.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @Test
    void allowsHappyPathTransitions() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void forbidsSkippingAndLeavingFinalStates() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.NEW)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID)).isFalse();
    }

    @Test
    void onlyNewAndPaidAreCancellable() {
        assertThat(OrderStatus.NEW.isCancellable()).isTrue();
        assertThat(OrderStatus.PAID.isCancellable()).isTrue();
        assertThat(OrderStatus.SHIPPED.isCancellable()).isFalse();
        assertThat(OrderStatus.DELIVERED.isCancellable()).isFalse();
        assertThat(OrderStatus.CANCELLED.isCancellable()).isFalse();
    }

    @Test
    void orderRecalculatesTotalFromItems() {
        Product phone = product(1L, "Phone", "100.50", 10, category(1L, "C"));
        Product case_ = product(2L, "Case", "9.50", 10, category(1L, "C"));
        Order order = new Order(null, null);
        order.addItem(new OrderItem(phone, 2, phone.getPrice()));
        order.addItem(new OrderItem(case_, 1, case_.getPrice()));

        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("210.50"));
        assertThat(order.getItems()).allMatch(item -> item.getOrder() == order);
    }

    @Test
    void productStockCannotGoNegative() {
        Product phone = product(1L, "Phone", "100", 3, category(1L, "C"));
        phone.decreaseStock(2);
        assertThat(phone.getStock()).isEqualTo(1);
        assertThatThrownBy(() -> phone.decreaseStock(2))
                .isInstanceOf(com.markethub.exception.ConflictException.class);
        phone.increaseStock(4);
        assertThat(phone.getStock()).isEqualTo(5);
    }
}
