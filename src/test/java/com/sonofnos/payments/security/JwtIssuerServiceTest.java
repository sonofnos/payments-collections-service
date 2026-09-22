package com.sonofnos.payments.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a real bug: jjwt's {@code signWith(key)} auto-selects
 * HS256/HS384/HS512 purely from the secret's byte length, so a secret longer
 * than 32 bytes (e.g. a 64-byte hex string from `openssl rand -hex 32`, a
 * very natural way to generate one) silently produced an HS512-signed token
 * that the resource server's HS256-only NimbusJwtDecoder then rejected. This
 * only surfaced on the real Render deployment, not in local tests, because
 * the local test secret happened to be short enough to land on HS256 by
 * chance. The fix pins the algorithm explicitly; this test proves it holds
 * for secrets of several different lengths, not just the lucky one.
 */
class JwtIssuerServiceTest {

    @Test
    void alwaysSignsWithHs256_regardlessOfSecretLength() {
        // 32-byte, 48-byte, and 64-byte secrets would previously select
        // HS256, HS384, and HS512 respectively if left to jjwt's default.
        assertHeaderAlgIsHs256("a".repeat(32));
        assertHeaderAlgIsHs256("b".repeat(48));
        assertHeaderAlgIsHs256("c".repeat(64));
    }

    private void assertHeaderAlgIsHs256(String secret) {
        JwtProperties properties = new JwtProperties(secret, Duration.ofHours(1));
        JwtIssuerService issuer = new JwtIssuerService(properties);

        String token = issuer.issue("subject", List.of("PAYMENTS_READ"));
        String headerJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));

        assertThat(headerJson).contains("\"alg\":\"HS256\"");
    }
}
