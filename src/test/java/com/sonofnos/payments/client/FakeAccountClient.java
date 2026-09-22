package com.sonofnos.payments.client;

import com.sonofnos.payments.client.dto.AccountBalance;
import com.sonofnos.payments.client.dto.TransferResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fake for core-banking-service used across this repo's test
 * suite, so nothing here depends on that service actually being deployed.
 * Starts every account at a configurable balance and tracks debits/credits
 * so tests can assert on call counts too. Call counters are tracked
 * per-accountId (not as a single global counter) because this bean is a
 * Spring singleton that Testcontainers-backed integration tests share across
 * one JVM-wide context - a global counter would leak state between unrelated
 * tests that happen to share the account id.
 */
public class FakeAccountClient implements AccountClient {

    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Set<String> failingAccounts = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> balanceCallsByAccount = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> debitCallsByAccount = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> creditCallsByAccount = new ConcurrentHashMap<>();

    public void seedBalance(String accountId, BigDecimal amount) {
        balances.put(accountId, amount);
    }

    public void makeAccountFail(String accountId) {
        failingAccounts.add(accountId);
    }

    public int balanceCallCount(String accountId) {
        return balanceCallsByAccount.getOrDefault(accountId, new AtomicInteger()).get();
    }

    public int debitCallCount(String accountId) {
        return debitCallsByAccount.getOrDefault(accountId, new AtomicInteger()).get();
    }

    public int creditCallCount(String accountId) {
        return creditCallsByAccount.getOrDefault(accountId, new AtomicInteger()).get();
    }

    @Override
    public AccountBalance getBalance(String accountId) {
        balanceCallsByAccount.computeIfAbsent(accountId, k -> new AtomicInteger()).incrementAndGet();
        return new AccountBalance(accountId, balances.getOrDefault(accountId, BigDecimal.ZERO), "USD");
    }

    @Override
    public TransferResult debit(String accountId, BigDecimal amount, String currency, String reference) {
        debitCallsByAccount.computeIfAbsent(accountId, k -> new AtomicInteger()).incrementAndGet();
        if (failingAccounts.contains(accountId)) {
            throw new AccountClientException("simulated failure debiting " + accountId);
        }
        BigDecimal newBalance = balances.merge(accountId, amount.negate(), BigDecimal::add);
        return new TransferResult(accountId, newBalance, currency, reference);
    }

    @Override
    public TransferResult credit(String accountId, BigDecimal amount, String currency, String reference) {
        creditCallsByAccount.computeIfAbsent(accountId, k -> new AtomicInteger()).incrementAndGet();
        if (failingAccounts.contains(accountId)) {
            throw new AccountClientException("simulated failure crediting " + accountId);
        }
        BigDecimal newBalance = balances.merge(accountId, amount, BigDecimal::add);
        return new TransferResult(accountId, newBalance, currency, reference);
    }
}
