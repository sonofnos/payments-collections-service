package com.sonofnos.payments.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Date;

/**
 * Self-issued JWT support: this service is its own authorization server for
 * demo/testing purposes (no external IdP dependency), the same approach used
 * by core-banking-service. Tokens are HMAC-SHA256 signed and verified by
 * SecurityConfig's resource-server decoder using the same shared secret.
 */
@Service
public class JwtIssuerService {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtIssuerService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String subject, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.tokenTtl())))
                .issuer("payments-collections-service")
                .signWith(key)
                .compact();
    }
}
