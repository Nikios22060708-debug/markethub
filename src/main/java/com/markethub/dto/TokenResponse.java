package com.markethub.dto;

public record TokenResponse(String token, String tokenType, long expiresInSeconds, UserResponse user) {
}
