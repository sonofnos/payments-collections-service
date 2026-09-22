package com.sonofnos.payments.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStatusTest {

    @Test
    void allowsInitiatedToProcessing() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
    }

    @Test
    void allowsProcessingToSettled() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.SETTLED)).isTrue();
    }

    @Test
    void allowsProcessingToFailed() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void rejectsInitiatedToSettled_skippingProcessing() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.SETTLED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SETTLED", "FAILED"})
    void terminalStatesRejectAnyTransition(PaymentStatus terminal) {
        for (PaymentStatus next : PaymentStatus.values()) {
            assertThat(terminal.canTransitionTo(next)).isFalse();
        }
        assertThat(terminal.isTerminal()).isTrue();
    }

    @Test
    void paymentThrowsOnInvalidTransition() {
        Payment payment = new Payment("key-1", "acc-1", "acc-2",
                new java.math.BigDecimal("10.00"), "USD", "test");
        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.SETTLED))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void paymentAllowsValidTransitionSequence() {
        Payment payment = new Payment("key-2", "acc-1", "acc-2",
                new java.math.BigDecimal("10.00"), "USD", "test");
        payment.transitionTo(PaymentStatus.PROCESSING);
        payment.transitionTo(PaymentStatus.SETTLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
    }
}
