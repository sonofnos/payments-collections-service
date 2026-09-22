package com.sonofnos.payments.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureServiceTest {

    private final WebhookSignatureService service = new WebhookSignatureService("test-secret");

    @Test
    void validSignatureVerifies() {
        String payload = "{\"paymentId\":\"abc\",\"success\":true}";
        String signature = service.sign(payload);
        assertThat(service.isValid(payload, signature)).isTrue();
    }

    @Test
    void tamperedPayloadFailsVerification() {
        String payload = "{\"paymentId\":\"abc\",\"success\":true}";
        String signature = service.sign(payload);
        assertThat(service.isValid(payload + "x", signature)).isFalse();
    }

    @Test
    void wrongSecretFailsVerification() {
        String payload = "{\"paymentId\":\"abc\",\"success\":true}";
        String signature = new WebhookSignatureService("different-secret").sign(payload);
        assertThat(service.isValid(payload, signature)).isFalse();
    }

    @Test
    void blankSignatureIsInvalid() {
        assertThat(service.isValid("payload", "")).isFalse();
        assertThat(service.isValid("payload", null)).isFalse();
    }
}
