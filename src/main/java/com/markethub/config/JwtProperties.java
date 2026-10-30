package com.markethub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки JWT из application.yml (app.jwt.*).
 *
 * @param secret ключ HMAC-SHA256, не короче 32 байт
 * @param ttl    время жизни токена
 * @param issuer издатель токена (claim iss)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {

    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret должен быть не короче 32 байт");
        }
        if (ttl == null) {
            ttl = Duration.ofHours(2);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "markethub";
        }
    }
}
