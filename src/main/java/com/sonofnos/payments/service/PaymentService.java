package com.sonofnos.payments.service;

import com.sonofnos.payments.client.AccountClientException;
import com.sonofnos.payments.client.dto.TransferResult;
import com.sonofnos.payments.domain.Payment;
import com.sonofnos.payments.domain.PaymentStatus;
import com.sonofnos.payments.repository.PaymentRepository;
import com.sonofnos.payments.service.event.EventPublisher;
import com.sonofnos.payments.service.event.PaymentEvent;
import com.sonofnos.payments.web.dto.CollectionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String PAYMENT_EVENTS_STREAM = "payment-lifecycle-events";

    private final PaymentRepository paymentRepository;
    private final AccountBalanceService accountBalanceService;
    private final EventPublisher eventPublisher;

    public PaymentService(PaymentRepository paymentRepository,
                           AccountBalanceService accountBalanceService,
                           EventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.accountBalanceService = accountBalanceService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Initiates a collection. Idempotent on {@code request.idempotencyKey()}:
     * a replayed request with the same key returns the original payment
     * instead of processing it again. Concurrency safety comes from the
     * unique constraint on payments.idempotency_key - if two threads race to
     * insert the same key, the DB rejects the loser, which then just reads
     * back the winner's row rather than erroring.
     *
     * <p>Deliberately not wrapped in a single {@code @Transactional}: each
     * repository call already gets its own transaction from Spring Data, and
     * this method also makes an outbound HTTP call to core-banking-service.
     * Holding one long transaction open across a network call (and across a
     * failed-insert retry) is exactly the kind of thing that causes the
     * "transaction marked rollback-only" trap when the unique constraint
     * fires - keeping each write its own short transaction avoids it.
     */
    public Payment initiateCollection(CollectionRequest request) {
        var existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent replay for key {} -> existing payment {}",
                    request.idempotencyKey(), existing.get().getId());
            return existing.get();
        }

        Payment payment = new Payment(
                request.idempotencyKey(),
                request.debtorAccountId(),
                request.creditorAccountId(),
                request.amount(),
                request.currency(),
                request.narrative()
        );

        try {
            payment = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Lost the race to another concurrent request with the same key.
            return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> e);
        }

        publish(payment);
        processCollection(payment);
        return payment;
    }

    /**
     * Debits the payer's account via core-banking-service and leaves the
     * payment in PROCESSING - settlement (crediting the collector and moving
     * to SETTLED) happens asynchronously when the payment gateway calls the
     * webhook back. If the debit itself fails, there's nothing to wait for.
     */
    private void processCollection(Payment payment) {
        payment.transitionTo(PaymentStatus.PROCESSING);
        paymentRepository.saveAndFlush(payment);
        publish(payment);

        try {
            TransferResult debit = accountBalanceService.debit(
                    payment.getDebtorAccountId(), payment.getAmount(), payment.getCurrency(),
                    payment.getId().toString());
            log.debug("Debit leg reserved, new debtor balance {}", debit.newBalance());
        } catch (AccountClientException e) {
            log.warn("Collection {} failed against core-banking-service: {}", payment.getId(), e.getMessage());
            payment.markFailed(e.getMessage());
            paymentRepository.saveAndFlush(payment);
            publish(payment);
        }
    }

    @Transactional(readOnly = true)
    public Payment getById(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No payment with id " + id));
    }

    @Transactional(readOnly = true)
    public Payment getByIdempotencyKey(String key) {
        return paymentRepository.findByIdempotencyKey(key)
                .orElseThrow(() -> new NoSuchElementException("No payment with idempotency key " + key));
    }

    /**
     * Settles or fails a payment based on an inbound (HMAC-verified) gateway
     * webhook. Replays for an already-terminal payment are ignored rather
     * than re-applied, so a duplicate webhook delivery can't double-credit.
     * The entity's {@code @Version} column backstops the same race for two
     * *concurrent* non-terminal webhook deliveries: the status transition is
     * committed (optimistic-locked) before the credit call to
     * core-banking-service is made, so the loser of a concurrent race never
     * reaches the money-moving call at all.
     *
     * <p>This does mean a credit failure after the lock is won leaves the
     * payment SETTLED without the credit leg confirmed - there's no
     * distributed transaction across these two services. A production
     * version would use an outbox + retry (or a saga) here; documented as a
     * known limitation rather than papered over.
     */
    public Payment applyWebhookOutcome(UUID paymentId, boolean success, String reason) {
        Payment payment = getById(paymentId);
        if (payment.getStatus().isTerminal()) {
            log.info("Webhook for already-terminal payment {} ignored (status={})", paymentId, payment.getStatus());
            return payment;
        }
        if (success) {
            payment.transitionTo(PaymentStatus.SETTLED);
        } else {
            payment.markFailed(reason);
        }

        try {
            paymentRepository.saveAndFlush(payment);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            log.info("Lost race to settle payment {} concurrently, returning current state", paymentId);
            return getById(paymentId);
        }

        if (success) {
            accountBalanceService.credit(
                    payment.getCreditorAccountId(), payment.getAmount(), payment.getCurrency(),
                    payment.getId().toString());
        }
        publish(payment);
        return payment;
    }

    private void publish(Payment payment) {
        eventPublisher.publish(PAYMENT_EVENTS_STREAM, PaymentEvent.of(
                payment.getId(), payment.getIdempotencyKey(), payment.getStatus(),
                payment.getAmount(), payment.getCurrency()));
    }
}
