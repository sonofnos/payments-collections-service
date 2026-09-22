package com.sonofnos.payments.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body of the simulated payment-gateway settlement callback. */
public record WebhookPayload(
        @NotNull UUID paymentId,
        @NotNull Boolean success,
        String reason
) {
}
