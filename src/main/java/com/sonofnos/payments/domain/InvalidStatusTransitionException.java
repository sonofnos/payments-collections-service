package com.sonofnos.payments.domain;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Cannot transition payment from %s to %s".formatted(from, to));
    }
}
