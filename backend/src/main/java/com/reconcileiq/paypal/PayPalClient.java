package com.reconcileiq.paypal;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PayPalClient {

    private final RestClient client;
    private final String clientId;
    private final String clientSecret;
    private static final Logger log =
        LoggerFactory.getLogger(PayPalClient.class);

    public PayPalClient(
            RestClient paypalRestClient,
            @Value("${paypal.client-id:}") String clientId,
            @Value("${paypal.client-secret:}") String clientSecret) {

        this.client = paypalRestClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getAccessToken() {
        if (clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException(
                    "PayPal credentials are not configured. Set PAYPAL_CLIENT_ID and "
                    + "PAYPAL_CLIENT_SECRET, or run with reconcileiq.data-source=DEMO_SCENARIOS.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        JsonNode response = client.post()
                .uri("/v1/oauth2/token")
                .headers(headers -> headers.setBasicAuth(
                        clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("access_token").isMissingNode()) {
            throw new IllegalStateException(
                    "PayPal did not return an access token");
        }

        return response.path("access_token").asText();
    }

    public PayPalOrderResult createOrder(
            String merchantOrderId,
            BigDecimal amount,
            String currency) {

        String accessToken = getAccessToken();

        Map<String, Object> amountDetails = Map.of(
                "currency_code", currency,
                "value", amount.setScale(2).toPlainString()
        );

        Map<String, Object> purchaseUnit = Map.of(
                "reference_id", merchantOrderId,
                "custom_id", merchantOrderId,
                "amount", amountDetails
        );

        Map<String, Object> request = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(purchaseUnit),
                "payment_source", Map.of(
                        "paypal", Map.of(
                                "experience_context", Map.of(
                                        "return_url",
                                        "http://localhost:5173/payment/return",
                                        "cancel_url",
                                        "http://localhost:5173/payment/cancel",
                                        "user_action", "PAY_NOW"
                                )
                        )
                )
        );

        JsonNode response = client.post()
                .uri("/v2/checkout/orders")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("PayPal-Request-Id",
                            UUID.randomUUID().toString());
                })
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("id").isMissingNode()) {
            throw new IllegalStateException(
                    "PayPal did not return an order ID");
        }

        String paypalOrderId = response.path("id").asText();

        String approvalUrl = null;
        JsonNode links = response.path("links");

        for (JsonNode link : links) {
            if ("payer-action".equals(link.path("rel").asText())
                    || "approve".equals(link.path("rel").asText())) {
                approvalUrl = link.path("href").asText();
                break;
            }
        }

        if (approvalUrl == null || approvalUrl.isBlank()) {
            throw new IllegalStateException(
                    "PayPal created an order but returned no approval URL");
        }

        return new PayPalOrderResult(paypalOrderId, approvalUrl);
    }

    public JsonNode captureOrder(String paypalOrderId) {
        String accessToken = getAccessToken();
    
        try {
            JsonNode response = client.post()
                    .uri("/v2/checkout/orders/{orderId}/capture",
                            paypalOrderId)
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.setContentType(
                                org.springframework.http.MediaType.APPLICATION_JSON);
                        headers.set("PayPal-Request-Id",
                                java.util.UUID.randomUUID().toString());
                    })
                    .body(java.util.Map.of())
                    .retrieve()
                    .body(JsonNode.class);
    
            if (response == null) {
                throw new IllegalStateException(
                        "PayPal returned an empty capture response");
            }
    
            return response;
    
        } catch (RestClientResponseException e) {
            log.error(
                    "PayPal capture failed. HTTP status: {}, response: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString()
            );
    
            throw e;
        }
    }

    public JsonNode getOrder(String paypalOrderId) {
        String accessToken = getAccessToken();
    
        return client.get()
                .uri("/v2/checkout/orders/{orderId}", paypalOrderId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class);
    }

    public record PayPalOrderResult(
            String paypalOrderId,
            String approvalUrl) {
    }
}