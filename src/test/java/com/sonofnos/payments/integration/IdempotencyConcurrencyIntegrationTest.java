package com.sonofnos.payments.integration;

import com.sonofnos.payments.domain.Payment;
import com.sonofnos.payments.repository.PaymentRepository;
import com.sonofnos.payments.service.PaymentService;
import com.sonofnos.payments.web.dto.CollectionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The standout correctness test: fire N concurrent requests carrying the
 * exact same idempotency key and prove exactly one Payment row is created,
 * and every caller's response points at that same payment. The unique DB
 * constraint on idempotency_key is what actually enforces this under real
 * concurrency (in-memory checks alone would race).
 */
class IdempotencyConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void concurrentReplaysWithSameIdempotencyKeyProduceExactlyOnePayment() throws InterruptedException {
        String idempotencyKey = "concurrent-key-" + System.nanoTime();
        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<AtomicReference<Payment>> results = IntStream.range(0, threadCount)
                .mapToObj(i -> new AtomicReference<Payment>())
                .collect(Collectors.toList());

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    CollectionRequest request = new CollectionRequest(
                            idempotencyKey, "debtor-1", "creditor-1",
                            new BigDecimal("25.00"), "USD", "concurrent test");
                    results.get(idx).set(paymentService.initiateCollection(request));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        Set<java.util.UUID> distinctPaymentIds = results.stream()
                .map(AtomicReference::get)
                .filter(java.util.Objects::nonNull)
                .map(Payment::getId)
                .collect(Collectors.toSet());

        assertThat(distinctPaymentIds).hasSize(1);

        long rowsInDb = paymentRepository.findAll().stream()
                .filter(p -> p.getIdempotencyKey().equals(idempotencyKey))
                .count();
        assertThat(rowsInDb).isEqualTo(1);
    }
}
