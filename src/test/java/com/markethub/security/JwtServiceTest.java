package com.markethub.security;

import com.markethub.config.JwtProperties;
import com.markethub.entity.Role;
import com.markethub.entity.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import static com.markethub.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-abcdef";

    private final SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    private final JwtService jwtService = new JwtService(
            new NimbusJwtEncoder(new ImmutableSecret<>(key)),
            new JwtProperties(SECRET, Duration.ofMinutes(30), "markethub"),
            Clock.systemUTC());

    @Test
    void generatedTokenContainsUserClaimsAndIsVerifiable() {
        User bob = user(42L, "bob", Role.ADMIN, Role.CUSTOMER);

        Jwt jwt = decoder.decode(jwtService.generateToken(bob));
        AuthUser authUser = AuthUser.from(jwt);

        assertThat(jwt.getSubject()).isEqualTo("bob");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("markethub");
        assertThat(authUser.id()).isEqualTo(42L);
        assertThat(authUser.roles()).containsExactlyInAnyOrder("ADMIN", "CUSTOMER");
        assertThat(jwtService.getTtlSeconds()).isEqualTo(1800);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        SecretKey otherKey = new SecretKeySpec("another-secret-key-0123456789-abcdefgh".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        JwtDecoder otherDecoder = NimbusJwtDecoder.withSecretKey(otherKey).macAlgorithm(MacAlgorithm.HS256).build();
        String token = jwtService.generateToken(user(1L, "eve", Role.CUSTOMER));

        assertThatThrownBy(() -> otherDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void propertiesValidateSecretLengthAndApplyDefaults() {
        assertThatThrownBy(() -> new JwtProperties("short", null, null))
                .isInstanceOf(IllegalArgumentException.class);

        JwtProperties defaults = new JwtProperties(SECRET, null, " ");
        assertThat(defaults.ttl()).isEqualTo(Duration.ofHours(2));
        assertThat(defaults.issuer()).isEqualTo("markethub");
    }
}
