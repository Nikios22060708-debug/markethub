package com.markethub.dto;

import com.markethub.entity.Role;

public record RoleResponse(Long id, String name) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}
