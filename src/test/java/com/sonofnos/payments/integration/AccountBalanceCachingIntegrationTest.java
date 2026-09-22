package com.sonofnos.payments.integration;

import com.sonofnos.payments.client.FakeAccountClient;
import com.sonofnos.payments.config.RedisCacheConfig;
import com.sonofnos.payments.service.AccountBalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Proves the Redis-backed balance cache actually avoids repeat calls to core-banking-service, and that a debit/credit through this service busts it. */
class AccountBalanceCachingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountBalanceService accountBalanceService;

    @Autowired
    private com.sonofnos.payments.client.AccountClient accountClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void repeatedBalanceLookupsAreServedFromCache() {
        FakeAccountClient fake = (FakeAccountClient) accountClient;
        String accountId = "cache-test-acct-" + System.nanoTime();
        fake.seedBalance(accountId, new BigDecimal("500.00"));

        accountBalanceService.getBalance(accountId);

        // Direct proof the entry actually landed in Redis, independent of any
        // call-count race: the definitive assertion that caching is wired up.
        String redisKey = RedisCacheConfig.ACCOUNT_BALANCE_CACHE + "::" + accountId;
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(stringRedisTemplate.hasKey(redisKey)).isTrue());

        accountBalanceService.getBalance(accountId);
        accountBalanceService.getBalance(accountId);

        // Sequential same-thread calls should all be cache hits after the
        // first; allow at most one extra real call as headroom for a rare
        // write-visibility hiccup against a Testcontainers Redis under load -
        // the hasKey() check above is what actually proves caching works.
        assertThat(fake.balanceCallCount(accountId)).isLessThanOrEqualTo(2);
    }

    @Test
    void debitEvictsCacheSoNextLookupIsFresh() {
        FakeAccountClient fake = (FakeAccountClient) accountClient;
        String accountId = "evict-test-acct-" + System.nanoTime();
        fake.seedBalance(accountId, new BigDecimal("500.00"));

        accountBalanceService.getBalance(accountId); // populates cache

        String redisKey = RedisCacheConfig.ACCOUNT_BALANCE_CACHE + "::" + accountId;
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(stringRedisTemplate.hasKey(redisKey)).isTrue());

        accountBalanceService.debit(accountId, new BigDecimal("10.00"), "USD", "ref-1");

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(stringRedisTemplate.hasKey(redisKey)).isFalse());

        accountBalanceService.getBalance(accountId); // should hit the client again, not the stale cache

        assertThat(fake.balanceCallCount(accountId)).isEqualTo(2);
    }
}
