package com.markethub.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record RolesUpdateRequest(@NotEmpty Set<String> roles) {
}
