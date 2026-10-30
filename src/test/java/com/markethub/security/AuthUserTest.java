package com.markethub.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUserTest {

    @Test
    void readsIdUsernameAndRolesFromJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("maria")
                .claim("uid", 5L)
                .claim("roles", List.of("MANAGER"))
                .build();

        AuthUser user = AuthUser.from(jwt);

        assertThat(user.id()).isEqualTo(5L);
        assertThat(user.username()).isEqualTo("maria");
        assertThat(user.isStaff()).isTrue();
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void customerIsNotStaff() {
        AuthUser user = new AuthUser(1L, "u", java.util.Set.of("CUSTOMER"));
        assertThat(user.isStaff()).isFalse();
    }

    @Test
    void failsWithoutUserId() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("x").build();
        assertThatThrownBy(() -> AuthUser.from(jwt)).isInstanceOf(IllegalStateException.class);
    }
}
