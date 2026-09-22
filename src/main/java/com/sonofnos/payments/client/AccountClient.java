package com.sonofnos.payments.client;

import com.sonofnos.payments.client.dto.AccountBalance;
import com.sonofnos.payments.client.dto.TransferResult;

import java.math.BigDecimal;

/**
 * Boundary between this service and core-banking-service. Two implementations
 * exist: {@link HttpAccountClient} (real HTTP over RestClient, used at runtime)
 * and a fake used in tests so the test suite never depends on that other
 * service being deployed.
 */
public interface AccountClient {

    AccountBalance getBalance(String accountId);

    TransferResult debit(String accountId, BigDecimal amount, String currency, String reference);

    TransferResult credit(String accountId, BigDecimal amount, String currency, String reference);
}
