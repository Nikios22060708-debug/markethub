package com.markethub.dto;

import com.markethub.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(@NotNull Long orderId, @NotNull PaymentMethod method) {
}
