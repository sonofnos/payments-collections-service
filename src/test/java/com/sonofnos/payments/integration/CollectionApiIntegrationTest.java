package com.sonofnos.payments.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
