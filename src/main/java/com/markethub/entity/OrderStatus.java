package com.markethub.entity;

/**
 * Жизненный цикл заказа: NEW → PAID → SHIPPED → DELIVERED, отмена возможна из NEW и PAID.
 */
public enum OrderStatus {
    NEW,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case NEW -> next == PAID || next == CANCELLED;
            case PAID -> next == SHIPPED || next == CANCELLED;
            case SHIPPED -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    public boolean isCancellable() {
        return canTransitionTo(CANCELLED);
    }
}
