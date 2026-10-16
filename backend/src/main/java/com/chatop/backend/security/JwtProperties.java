package com.chatop.backend.security;

import java.time.Duration;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {
    public JwtProperties {
        if (secret == null || Base64.getDecoder().decode(secret).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes encoded in Base64");
        }
        if (issuer == null || issuer.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("A JWT issuer and a positive lifetime are required");
        }
    }
}
