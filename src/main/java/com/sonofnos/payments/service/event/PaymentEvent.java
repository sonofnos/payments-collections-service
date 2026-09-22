package com.sonofnos.payments.service.event;

import com.sonofnos.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentEvent(
        UUID paymentId,
        String idempotencyKey,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
    public static PaymentEvent of(UUID paymentId, String idempotencyKey, PaymentStatus status,
                                   BigDecimal amount, String currency) {
        return new PaymentEvent(paymentId, idempotencyKey, status, amount, currency, Instant.now());
    }
}
