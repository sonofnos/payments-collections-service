package com.sonofnos.payments.client;

import com.sonofnos.payments.client.dto.AccountBalance;
import com.sonofnos.payments.client.dto.TransferResult;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Real HTTP implementation calling core-banking-service's REST API. Talks to
 * the documented contract:
 *   GET  /api/accounts/{id}/balance
 *   POST /api/accounts/{id}/debit   {amount, currency, reference}
 *   POST /api/accounts/{id}/credit  {amount, currency, reference}
 */
public class HttpAccountClient implements AccountClient {

    private final RestClient restClient;

    public HttpAccountClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AccountBalance getBalance(String accountId) {
        try {
            return restClient.get()
                    .uri("/api/accounts/{id}/balance", accountId)
                    .retrieve()
                    .body(AccountBalance.class);
        } catch (RestClientResponseException e) {
            throw new AccountClientException(
                    "core-banking-service balance lookup failed for account %s: %s"
                            .formatted(accountId, e.getStatusCode()), e);
        } catch (Exception e) {
            throw new AccountClientException(
                    "core-banking-service unreachable for balance lookup on account %s".formatted(accountId), e);
        }
    }

    @Override
    public TransferResult debit(String accountId, BigDecimal amount, String currency, String reference) {
        return post(accountId, "debit", amount, currency, reference);
    }

    @Override
    public TransferResult credit(String accountId, BigDecimal amount, String currency, String reference) {
        return post(accountId, "credit", amount, currency, reference);
    }

    private TransferResult post(String accountId, String operation, BigDecimal amount, String currency, String reference) {
        try {
            return restClient.post()
                    .uri("/api/accounts/{id}/{op}", accountId, operation)
                    .body(Map.of(
                            "amount", amount,
                            "currency", currency,
                            "reference", reference
                    ))
                    .retrieve()
                    .body(TransferResult.class);
        } catch (RestClientResponseException e) {
            throw new AccountClientException(
                    "core-banking-service %s failed for account %s: %s"
                            .formatted(operation, accountId, e.getStatusCode()), e);
        } catch (Exception e) {
            throw new AccountClientException(
                    "core-banking-service unreachable for %s on account %s".formatted(operation, accountId), e);
        }
    }
}
