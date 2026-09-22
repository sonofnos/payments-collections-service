package com.sonofnos.payments.service;

import com.sonofnos.payments.client.AccountClient;
import com.sonofnos.payments.client.dto.AccountBalance;
import com.sonofnos.payments.client.dto.TransferResult;
import com.sonofnos.payments.config.RedisCacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Caches core-banking-service balance lookups for a short TTL (see
 * RedisCacheConfig) to cut chatter on repeat status checks, and evicts the
 * cache for any account this service just moved money through - a stale
 * cached balance right after a debit/credit would be a correctness bug.
 */
@Service
public class AccountBalanceService {

    private final AccountClient accountClient;

    public AccountBalanceService(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    @Cacheable(cacheNames = RedisCacheConfig.ACCOUNT_BALANCE_CACHE, key = "#accountId")
    public AccountBalance getBalance(String accountId) {
        return accountClient.getBalance(accountId);
    }

    @CacheEvict(cacheNames = RedisCacheConfig.ACCOUNT_BALANCE_CACHE, key = "#accountId")
    public TransferResult debit(String accountId, BigDecimal amount, String currency, String reference) {
        return accountClient.debit(accountId, amount, currency, reference);
    }

    @CacheEvict(cacheNames = RedisCacheConfig.ACCOUNT_BALANCE_CACHE, key = "#accountId")
    public TransferResult credit(String accountId, BigDecimal amount, String currency, String reference) {
        return accountClient.credit(accountId, amount, currency, reference);
    }
}
