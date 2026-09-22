package com.sonofnos.payments.config;

import com.sonofnos.payments.client.AccountClient;
import com.sonofnos.payments.client.HttpAccountClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.config.RequestConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CoreBankingProperties.class)
public class AccountClientConfig {

    @Bean
    public RestClient coreBankingRestClient(CoreBankingProperties props) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(toTimeout(props.connectTimeout()))
                .setResponseTimeout(toTimeout(props.readTimeout()))
                .build();
        var httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
        var factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    public AccountClient accountClient(RestClient coreBankingRestClient) {
        return new HttpAccountClient(coreBankingRestClient);
    }

    private static org.apache.hc.core5.util.Timeout toTimeout(Duration duration) {
        return org.apache.hc.core5.util.Timeout.of(duration);
    }
}
