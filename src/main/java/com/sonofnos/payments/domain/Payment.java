package com.sonofnos.payments.domain;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Implements {@link Persistable} because the id is a client-generated UUID
 * assigned in the constructor rather than a DB identity column. Without this,
 * Spring Data JPA's default "is this new?" heuristic falls back to the
 * {@code @Version} field, sees the initial value 0 on every save call (not
 * just the first), and calls {@code persist()} a second time instead of
 * {@code merge()} - which surfaces as a spurious
 * ObjectOptimisticLockingFailureException on the second write of a brand new
 * row. {@code isNew} flips to false the moment the row is actually persisted
 * or loaded.
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_idempotency_key", columnList = "idempotencyKey", unique = true)
})
public class Payment implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    /** Client-supplied idempotency key; unique so replay never double-charges. */
    @Column(nullable = false, unique = true, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String debtorAccountId;

    @Column(nullable = false, length = 64)
    private String creditorAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String narrative;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Payment() {
    }

    public Payment(String idempotencyKey, String debtorAccountId, String creditorAccountId,
                   BigDecimal amount, String currency, String narrative) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.debtorAccountId = debtorAccountId;
        this.creditorAccountId = creditorAccountId;
        this.amount = amount;
        this.currency = currency;
        this.narrative = narrative;
        this.status = PaymentStatus.INITIATED;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void transitionTo(PaymentStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(this.status, next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = reason;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDebtorAccountId() {
        return debtorAccountId;
    }

    public String getCreditorAccountId() {
        return creditorAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNarrative() {
        return narrative;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
