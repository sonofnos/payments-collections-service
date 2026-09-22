package com.sonofnos.payments.service;

import com.sonofnos.payments.client.FakeAccountClient;
import com.sonofnos.payments.domain.Payment;
import com.sonofnos.payments.domain.PaymentStatus;
import com.sonofnos.payments.repository.PaymentRepository;
import com.sonofnos.payments.service.event.EventPublisher;
import com.sonofnos.payments.web.dto.CollectionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private FakeAccountClient fakeAccountClient;
    private EventPublisher eventPublisher;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        fakeAccountClient = new FakeAccountClient();
        fakeAccountClient.seedBalance("debtor-1", new BigDecimal("1000.00"));
        eventPublisher = mock(EventPublisher.class);
        AccountBalanceService accountBalanceService = new AccountBalanceService(fakeAccountClient);
        paymentService = new PaymentService(paymentRepository, accountBalanceService, eventPublisher);

        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CollectionRequest request(String idempotencyKey) {
        return new CollectionRequest(idempotencyKey, "debtor-1", "creditor-1",
                new BigDecimal("100.00"), "USD", "invoice #1");
    }

    @Test
    void newCollectionIsPersistedAndDebited() {
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        Payment payment = paymentService.initiateCollection(request("key-1"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(fakeAccountClient.debitCallCount("debtor-1")).isEqualTo(1);
        assertThat(fakeAccountClient.creditCallCount("creditor-1")).isEqualTo(0); // credit happens on webhook settlement
        verify(eventPublisher, atLeastOnce()).publish(anyString(), any());
    }

    @Test
    void replayWithSameIdempotencyKeyReturnsOriginal_doesNotDebitAgain() {
        Payment existing = new Payment("key-1", "debtor-1", "creditor-1",
                new BigDecimal("100.00"), "USD", "invoice #1");
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Payment result = paymentService.initiateCollection(request("key-1"));

        assertThat(result).isSameAs(existing);
        assertThat(fakeAccountClient.debitCallCount("debtor-1")).isEqualTo(0);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void debitFailureMarksPaymentFailed() {
        fakeAccountClient.makeAccountFail("debtor-1");
        when(paymentRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());

        Payment payment = paymentService.initiateCollection(request("key-2"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).contains("simulated failure");
    }

    @Test
    void webhookSettlementCreditsCreditorAndMarksSettled() {
        Payment payment = new Payment("key-3", "debtor-1", "creditor-1",
                new BigDecimal("50.00"), "USD", "invoice #3");
        payment.transitionTo(PaymentStatus.PROCESSING);
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        Payment settled = paymentService.applyWebhookOutcome(id, true, null);

        assertThat(settled.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(fakeAccountClient.creditCallCount("creditor-1")).isEqualTo(1);
    }

    @Test
    void webhookForAlreadyTerminalPaymentIsIgnored_doesNotDoubleCredit() {
        Payment payment = new Payment("key-4", "debtor-1", "creditor-1",
                new BigDecimal("50.00"), "USD", "invoice #4");
        payment.transitionTo(PaymentStatus.PROCESSING);
        payment.transitionTo(PaymentStatus.SETTLED);
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        paymentService.applyWebhookOutcome(id, true, null);

        assertThat(fakeAccountClient.creditCallCount("creditor-1")).isEqualTo(0);
        verify(paymentRepository, never()).saveAndFlush(any());
    }
}
