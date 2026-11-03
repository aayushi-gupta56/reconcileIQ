package com.reconcileiq.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Component
public class PayPalCaptureRecorder {

    public record CaptureRecord(
            String paypalOrderId,
            String captureId,
            String customId,
            BigDecimal amount,
            String currency,
            String status,
            LocalDateTime capturedAt
    ) {}

    public CaptureRecord firstCapture(String paypalOrderId, JsonNode captureResponse) {
        if (captureResponse == null) {
            throw new IllegalStateException("PayPal returned an empty capture response");
        }
        JsonNode purchaseUnits = captureResponse.path("purchase_units");
        if (!purchaseUnits.isArray() || purchaseUnits.isEmpty()) {
            throw new IllegalStateException("PayPal response contains no purchase units");
        }
        JsonNode unit = purchaseUnits.get(0);
        String customId = textOrNull(unit.path("custom_id"));
        JsonNode captures = unit.path("payments").path("captures");
        if (!captures.isArray() || captures.isEmpty()) {
            throw new IllegalStateException("PayPal response contains no capture records");
        }
        JsonNode capture = captures.get(0);
        String captureId = capture.path("id").asText();
        String status = capture.path("status").asText();
        String value = capture.path("amount").path("value").asText();
        String currency = capture.path("amount").path("currency_code").asText();
        if (captureId.isBlank() || value.isBlank() || currency.isBlank()) {
            throw new IllegalStateException("PayPal capture response is missing required fields");
        }
        LocalDateTime capturedAt = null;
        String createTime = capture.path("create_time").asText();
        if (!createTime.isBlank()) {
            capturedAt = OffsetDateTime.parse(createTime).toLocalDateTime();
        }
        return new CaptureRecord(
                paypalOrderId,
                captureId,
                customId,
                new BigDecimal(value),
                currency,
                status,
                capturedAt
        );
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.asText().isBlank()) {
            return null;
        }
        return node.asText();
    }
}
