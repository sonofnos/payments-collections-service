package com.sonofnos.payments.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a payment/collection. Transitions are enforced by
 * {@link PaymentStatus#canTransitionTo(PaymentStatus)} so the service layer
 * never has to trust ad-hoc status writes.
 */
public enum PaymentStatus {
    INITIATED,
    PROCESSING,
    SETTLED,
    FAILED;

    private static final Set<PaymentStatus> TERMINAL = EnumSet.of(SETTLED, FAILED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(PaymentStatus next) {
        if (this == next) {
            return false;
        }
        return switch (this) {
            case INITIATED -> next == PROCESSING || next == FAILED;
            case PROCESSING -> next == SETTLED || next == FAILED;
            case SETTLED, FAILED -> false; // terminal states
        };
    }
}
