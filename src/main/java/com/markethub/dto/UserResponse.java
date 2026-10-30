package com.markethub.dto;

import com.markethub.entity.User;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public record UserResponse(Long id, String username, String email, String fullName, boolean enabled,
                           Set<String> roles, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.isEnabled(), new TreeSet<>(user.getRoleNames()), user.getCreatedAt());
    }
}
