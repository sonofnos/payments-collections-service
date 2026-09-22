package com.sonofnos.payments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "core-banking")
public record CoreBankingProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public CoreBankingProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
