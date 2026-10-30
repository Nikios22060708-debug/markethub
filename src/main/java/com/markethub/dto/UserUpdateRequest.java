package com.markethub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Обновление пользователя. Пароль необязателен: если не передан, остаётся прежним. */
public record UserUpdateRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 150) String fullName,
        @NotNull Boolean enabled,
        @Size(min = 6, max = 100) String password) {
}
