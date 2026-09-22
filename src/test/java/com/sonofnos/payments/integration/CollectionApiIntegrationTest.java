package com.sonofnos.payments.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonofnos.payments.client.AccountClient;
import com.sonofnos.payments.client.FakeAccountClient;
import com.sonofnos.payments.security.JwtIssuerService;
import com.sonofnos.payments.service.WebhookSignatureService;
import com.sonofnos.payments.web.dto.CollectionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CollectionApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtIssuerService jwtIssuerService;
    @Autowired
    private WebhookSignatureService webhookSignatureService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AccountClient accountClient;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private String bearerToken() {
        return "Bearer " + jwtIssuerService.issue("test-user", List.of("PAYMENTS_WRITE", "PAYMENTS_READ"));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc().perform(get("/api/collections/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void initiateThenFetchByIdRoundTrips() throws Exception {
        CollectionRequest request = new CollectionRequest(
                "api-test-key-" + System.nanoTime(), "debtor-1", "creditor-1",
                new BigDecimal("42.50"), "USD", "test narrative");

        String body = mockMvc().perform(post("/api/collections")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(body).get("id").asText();

        mockMvc().perform(get("/api/collections/" + id).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void replayingIdempotencyKeyReturnsSameId() throws Exception {
        String key = "replay-key-" + System.nanoTime();
        CollectionRequest request = new CollectionRequest(
                key, "debtor-1", "creditor-1", new BigDecimal("10.00"), "USD", null);

        String first = mockMvc().perform(post("/api/collections")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc().perform(post("/api/collections")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        String firstId = objectMapper.readTree(first).get("id").asText();
        String secondId = objectMapper.readTree(second).get("id").asText();
        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void webhookWithValidSignatureSettlesPayment() throws Exception {
        CollectionRequest request = new CollectionRequest(
                "webhook-key-" + System.nanoTime(), "debtor-1", "creditor-1",
                new BigDecimal("15.00"), "USD", null);
        String created = mockMvc().perform(post("/api/collections")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();

        String webhookBody = "{\"paymentId\":\"" + id + "\",\"success\":true}";
        String signature = webhookSignatureService.sign(webhookBody);

        mockMvc().perform(post("/api/webhooks/gateway-settlement")
                        .header("X-Webhook-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    /**
     * Real-Postgres regression test for a bug the MockMvc-with-mocked-repository
     * unit tests couldn't catch: on a debit failure, PaymentService used to save
     * the payment a second time without reassigning the entity returned by the
     * first saveAndFlush (JPA merge() returns a *different* managed instance),
     * so the second save carried a stale @Version and Hibernate rejected it as
     * a lost update - a 500 on a perfectly ordinary, single-request failure
     * path, only visible against a real Hibernate merge, not a stubbed one.
     */
    @Test
    void debitFailureAgainstRealPostgresDoesNotThrowStaleVersionError() throws Exception {
        String failingDebtor = "failing-debtor-" + System.nanoTime();
        ((FakeAccountClient) accountClient).makeAccountFail(failingDebtor);

        CollectionRequest request = new CollectionRequest(
                "fail-key-" + System.nanoTime(), failingDebtor, "creditor-1",
                new BigDecimal("20.00"), "USD", null);

        mockMvc().perform(post("/api/collections")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.containsString("simulated failure")));
    }

    @Test
    void webhookWithBadSignatureIsRejected() throws Exception {
        String webhookBody = "{\"paymentId\":\"" + UUID.randomUUID() + "\",\"success\":true}";

        mockMvc().perform(post("/api/webhooks/gateway-settlement")
                        .header("X-Webhook-Signature", "not-the-real-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody))
                .andExpect(status().isUnauthorized());
    }
}
