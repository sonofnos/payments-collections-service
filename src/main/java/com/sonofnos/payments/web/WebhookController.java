package com.sonofnos.payments.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonofnos.payments.domain.Payment;
import com.sonofnos.payments.service.PaymentService;
import com.sonofnos.payments.service.WebhookSignatureService;
import com.sonofnos.payments.web.dto.PaymentResponse;
import com.sonofnos.payments.web.dto.WebhookPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Simulated payment-gateway settlement callback. The gateway signs the raw
 * request body with a shared HMAC-SHA256 secret and sends it in
 * X-Webhook-Signature; we verify before trusting the payload at all. This
 * endpoint is deliberately not behind the JWT filter chain (a gateway isn't
 * an OAuth2 client of ours) - HMAC is the auth here, exactly as a real
 * payment gateway webhook works.
 */
@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks", description = "Signed payment-gateway settlement callback")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentService paymentService;
    private final WebhookSignatureService signatureService;
    private final ObjectMapper objectMapper;

    public WebhookController(PaymentService paymentService, WebhookSignatureService signatureService,
                              ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/gateway-settlement")
    @Operation(summary = "Gateway settlement callback (HMAC-SHA256 signed, header X-Webhook-Signature)")
    public ResponseEntity<?> handleSettlement(@RequestBody String rawBody,
                                               @RequestHeader("X-Webhook-Signature") String signature) throws Exception {
        if (!signatureService.isValid(rawBody, signature)) {
            log.warn("Rejected webhook with invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        WebhookPayload payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        Payment payment = paymentService.applyWebhookOutcome(payload.paymentId(), payload.success(), payload.reason());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }
}
