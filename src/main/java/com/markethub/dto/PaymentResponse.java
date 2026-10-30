package com.markethub.dto;

import com.markethub.entity.Payment;
import com.markethub.entity.PaymentMethod;
import com.markethub.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long orderId, BigDecimal amount, PaymentMethod method, PaymentStatus status,
                              String transactionId, Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrder().getId(), payment.getAmount(),
                payment.getMethod(), payment.getStatus(), payment.getTransactionId(), payment.getCreatedAt());
    }
}
