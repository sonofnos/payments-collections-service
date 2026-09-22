package com.sonofnos.payments.web;

import com.sonofnos.payments.domain.Payment;
import com.sonofnos.payments.service.PaymentService;
import com.sonofnos.payments.web.dto.CollectionRequest;
import com.sonofnos.payments.web.dto.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/collections")
@Tag(name = "Collections", description = "Initiate collections and check payment status")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PAYMENTS_WRITE')")
    @Operation(summary = "Initiate a collection",
            description = "Idempotent on idempotencyKey: replaying the same key returns the original payment.")
    public ResponseEntity<PaymentResponse> initiate(@Valid @RequestBody CollectionRequest request) {
        Payment payment = paymentService.initiateCollection(request);
        return ResponseEntity
                .created(URI.create("/api/collections/" + payment.getId()))
                .body(PaymentResponse.from(payment));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PAYMENTS_READ','PAYMENTS_WRITE')")
    @Operation(summary = "Get payment status by id")
    public PaymentResponse getById(@PathVariable UUID id) {
        return PaymentResponse.from(paymentService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PAYMENTS_READ','PAYMENTS_WRITE')")
    @Operation(summary = "Get payment status by idempotency key")
    public PaymentResponse getByIdempotencyKey(@RequestParam String idempotencyKey) {
        return PaymentResponse.from(paymentService.getByIdempotencyKey(idempotencyKey));
    }
}
