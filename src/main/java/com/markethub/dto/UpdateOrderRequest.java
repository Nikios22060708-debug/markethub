package com.markethub.dto;

import jakarta.validation.constraints.NotNull;

/** Пока заказ не отправлен, покупатель может сменить адрес доставки. */
public record UpdateOrderRequest(@NotNull Long addressId) {
}
