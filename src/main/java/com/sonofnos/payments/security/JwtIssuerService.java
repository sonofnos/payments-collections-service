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
 *
 * <p>The algorithm is pinned explicitly to HS256 rather than left to jjwt's
 * {@code signWith(key)} auto-selection, which infers HS256/HS384/HS512 purely
 * from the secret's byte length (a 64-byte secret silently produces an
 * HS512-signed token). {@link SecurityConfig}'s {@code NimbusJwtDecoder}
 * defaults to expecting HS256 only, so a longer-than-32-byte secret (e.g.
 * {@code openssl rand -hex 32}, which is 64 ASCII bytes) previously signed
 * tokens the decoder then rejected with "Another algorithm expected" - a real
 * bug caught during the Render deployment smoke test, not in local testing,
 * because the test secret happened to be short enough to land on HS256 by
 * chance.
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
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
