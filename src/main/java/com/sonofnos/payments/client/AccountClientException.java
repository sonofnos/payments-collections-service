package com.sonofnos.payments.client;

/** Wraps any failure talking to core-banking-service (network, 4xx, 5xx). */
public class AccountClientException extends RuntimeException {
    public AccountClientException(String message) {
        super(message);
    }

    public AccountClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
