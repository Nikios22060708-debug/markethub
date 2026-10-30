package com.markethub.security;

import com.markethub.entity.Role;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;

/**
 * Текущий пользователь, восстановленный из JWT без обращения к БД.
 */
public record AuthUser(Long id, String username, Set<String> roles) {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLES = "roles";

    public static AuthUser from(Jwt jwt) {
        Object uid = jwt.getClaims().get(CLAIM_USER_ID);
        if (!(uid instanceof Number number)) {
            throw new IllegalStateException("В токене нет идентификатора пользователя");
        }
        List<String> roles = jwt.getClaimAsStringList(CLAIM_ROLES);
        return new AuthUser(number.longValue(), jwt.getSubject(), roles == null ? Set.of() : Set.copyOf(roles));
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    /** Сотрудник магазина: администратор или менеджер. */
    public boolean isStaff() {
        return isAdmin() || roles.contains(Role.MANAGER);
    }
}
