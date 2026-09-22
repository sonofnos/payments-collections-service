package com.sonofnos.payments.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration tokenTtl) {
    public JwtProperties {
        if (tokenTtl == null) {
            tokenTtl = Duration.ofHours(1);
        }
    }
}
