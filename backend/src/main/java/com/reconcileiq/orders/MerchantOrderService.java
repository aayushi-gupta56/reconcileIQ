package com.reconcileiq.orders;

import com.reconcileiq.paypal.PayPalClient;
import com.reconcileiq.paypal.PayPalClient.PayPalOrderResult;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.reconcileiq.paypal.PayPalTransaction;
import com.reconcileiq.paypal.PayPalTransactionRepository;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.Map;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.reconcileiq.orders.OrderDtos.*;

@Service
public class MerchantOrderService {

    private final MerchantOrderRepository repository;
    private final PayPalClient paypalClient;
    private final PayPalTransactionRepository transactionRepository;

    public MerchantOrderService(
        MerchantOrderRepository repository,
        PayPalClient paypalClient,
        PayPalTransactionRepository transactionRepository) {

        this.repository = repository;
        this.paypalClient = paypalClient;
        this.transactionRepository = transactionRepository;
    }

    public MerchantOrderResponse createOrder(CreateOrderRequest request) {
        if (request == null || request.amount() == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero");
        }

        if (request.amount().scale() > 2) {
            throw new IllegalArgumentException(
                    "Amount must have no more than two decimal places");
        }

        if (request.currency() == null
                || !request.currency().trim().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency must be a three-letter code, such as USD");
        }

        String currency = request.currency()
                .trim()
                .toUpperCase(Locale.ROOT);

        String merchantOrderId =
                "ORD-" + UUID.randomUUID().toString().substring(0, 12)
                        .toUpperCase(Locale.ROOT);

        MerchantOrder order = new MerchantOrder(
                merchantOrderId,
                request.amount(),
                currency
        );

        // Persist our own order before making the external API call.
        repository.save(order);

        try {
            PayPalOrderResult paypalOrder = paypalClient.createOrder(
                    merchantOrderId,
                    request.amount(),
                    currency
            );

            order.setPaypalOrderId(paypalOrder.paypalOrderId());
            order.setApprovalUrl(paypalOrder.approvalUrl());
            order.setStatus("AWAITING_APPROVAL");

            repository.save(order);

            return MerchantOrderResponse.from(order);

        } catch (RuntimeException exception) {
            order.setStatus("PAYPAL_CREATION_FAILED");
            repository.save(order);
            throw exception;
        }
    }

    public List<MerchantOrderResponse> listOrders() {
        return repository.findAllByOrderByIdDesc()
                .stream()
                .map(MerchantOrderResponse::from)
                .toList();
    }

    public Map<String, Object> capturePayment(String paypalOrderId) {
        if (paypalOrderId == null || paypalOrderId.isBlank()) {
            throw new IllegalArgumentException("PayPal order ID is required");
        }
    
        MerchantOrder order = repository.findByPaypalOrderId(paypalOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No merchant order found for this PayPal order"));
    
        if ("COMPLETED".equals(order.getStatus())) {
            return Map.of(
                    "merchantOrderId", order.getMerchantOrderId(),
                    "paypalOrderId", paypalOrderId,
                    "status", "COMPLETED",
                    "message", "Payment was already recorded as completed"
            );
        }
    
        JsonNode response = paypalClient.captureOrder(paypalOrderId);
        String orderStatus = response.path("status").asText();
    
        if (!"COMPLETED".equals(orderStatus)) {
            throw new IllegalStateException(
                    "PayPal order is not completed. Current status: " + orderStatus);
        }
    
        JsonNode purchaseUnits = response.path("purchase_units");
    
        if (!purchaseUnits.isArray() || purchaseUnits.isEmpty()) {
            throw new IllegalStateException(
                    "PayPal response contains no purchase units");
        }
    
        JsonNode captures = purchaseUnits.get(0)
                .path("payments")
                .path("captures");
    
        if (!captures.isArray() || captures.isEmpty()) {
            throw new IllegalStateException(
                    "PayPal response contains no capture records");
        }
    
        JsonNode capture = captures.get(0);
        String captureId = capture.path("id").asText();
        String captureStatus = capture.path("status").asText();
        String value = capture.path("amount").path("value").asText();
        String currency = capture.path("amount")
                .path("currency_code").asText();
    
        if (captureId.isBlank() || value.isBlank() || currency.isBlank()) {
            throw new IllegalStateException(
                    "PayPal capture response is missing required fields");
        }
    
        if (!"COMPLETED".equals(captureStatus)) {
            throw new IllegalStateException(
                    "Capture is not completed. Current status: " + captureStatus);
        }
    
        if (transactionRepository.findByCaptureId(captureId).isEmpty()) {
            LocalDateTime capturedAt = null;
            String createTime = capture.path("create_time").asText();
    
            if (!createTime.isBlank()) {
                capturedAt = OffsetDateTime.parse(createTime).toLocalDateTime();
            }
    
            PayPalTransaction transaction = new PayPalTransaction(
                    paypalOrderId,
                    captureId,
                    order.getMerchantOrderId(),
                    new java.math.BigDecimal(value),
                    currency,
                    captureStatus,
                    capturedAt
            );
    
            transactionRepository.save(transaction);
        }
    
        order.setStatus("COMPLETED");
        repository.save(order);
    
        return Map.of(
                "merchantOrderId", order.getMerchantOrderId(),
                "paypalOrderId", paypalOrderId,
                "captureId", captureId,
                "amount", value,
                "currency", currency,
                "status", captureStatus
        );
    }
}