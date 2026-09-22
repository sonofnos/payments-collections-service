package com.sonofnos.payments.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonofnos.payments.client.dto.AccountBalance;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    public static final String ACCOUNT_BALANCE_CACHE = "accountBalances";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        // Bound to the concrete AccountBalance type rather than
        // GenericJackson2JsonRedisSerializer's polymorphic "write a @class
        // property" approach: this cache only ever holds one value type, so a
        // type-specific serializer sidesteps needing default-typing activated
        // on the ObjectMapper at all (default typing here also fought with
        // records and the JacksonConfig bean shared with the webhook/event
        // paths - not worth the fragility for a single-type cache).
        var balanceSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, AccountBalance.class);

        RedisCacheConfiguration balanceConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .entryTtl(Duration.ofSeconds(30)) // short TTL: cheap to re-fetch from core-banking-service,
                                                   // but stale-for-long-periods is a correctness risk
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(balanceSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .withInitialCacheConfigurations(Map.of(ACCOUNT_BALANCE_CACHE, balanceConfig))
                .build();
    }
}
