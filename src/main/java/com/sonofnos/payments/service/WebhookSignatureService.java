package com.sonofnos.payments.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies HMAC-SHA256 signatures on inbound gateway webhooks. Signature is
 * computed over the raw request body using a shared secret, hex-encoded, and
 * compared in constant time to avoid a timing side-channel.
 */
@Service
public class WebhookSignatureService {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secretKeyBytes;

    public WebhookSignatureService(@Value("${webhook.hmac-secret}") String secret) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC signature", e);
        }
    }

    public boolean isValid(String payload, String providedSignatureHex) {
        if (providedSignatureHex == null || providedSignatureHex.isBlank()) {
            return false;
        }
        String expected = sign(payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignatureHex.trim().getBytes(StandardCharsets.UTF_8));
    }
}
