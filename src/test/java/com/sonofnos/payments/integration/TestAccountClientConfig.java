package com.sonofnos.payments.integration;

import com.sonofnos.payments.client.AccountClient;
import com.sonofnos.payments.client.FakeAccountClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Swaps the real HttpAccountClient for the in-memory fake in every
 * integration test - this repo's test suite must not depend on
 * core-banking-service actually being up.
 */
@TestConfiguration
public class TestAccountClientConfig {

    @Bean
    @Primary
    public AccountClient fakeAccountClient() {
        FakeAccountClient fake = new FakeAccountClient();
        fake.seedBalance("debtor-1", new java.math.BigDecimal("100000.00"));
        return fake;
    }
}
