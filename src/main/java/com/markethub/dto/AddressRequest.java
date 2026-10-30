package com.markethub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @Size(max = 50) String label,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 20) String zipCode) {
}
