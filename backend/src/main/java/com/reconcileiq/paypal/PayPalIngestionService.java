package com.reconcileiq.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.reconcileiq.config.DataSourceMode;
import com.reconcileiq.config.ReconcileIqProperties;
import com.reconcileiq.orders.MerchantOrder;
import com.reconcileiq.orders.MerchantOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PayPalIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PayPalIngestionService.class);

    private final ReconcileIqProperties properties;
    private final PayPalClient paypalClient;
    private final MerchantOrderRepository merchantOrderRepository;
    private final PayPalTransactionRepository transactionRepository;

    public PayPalIngestionService(
            ReconcileIqProperties properties,
            PayPalClient paypalClient,
            MerchantOrderRepository merchantOrderRepository,
            PayPalTransactionRepository transactionRepository) {
        this.properties = properties;
        this.paypalClient = paypalClient;
        this.merchantOrderRepository = merchantOrderRepository;
        this.transactionRepository = transactionRepository;
    }

    public int refreshIfLive() {
        if (properties.getDataSource() != DataSourceMode.LIVE_SANDBOX) {
            return 0;
        }
        return refreshFromKnownOrders();
    }

    public int refreshFromKnownOrders() {
        int stored = 0;
        List<MerchantOrder> orders = merchantOrderRepository.findAll();
        for (MerchantOrder order : orders) {
            if (order.getPaypalOrderId() == null || order.getPaypalOrderId().isBlank()) {
                continue;
            }
            try {
                JsonNode paypalOrder = paypalClient.getOrder(order.getPaypalOrderId());
                stored += upsertCaptures(order, paypalOrder);
            } catch (RuntimeException ex) {
                log.warn("Skipping PayPal order {}: {}", order.getPaypalOrderId(), ex.getMessage());
            }
        }
        searchRecentTransactions();
        return stored;
    }

    private int upsertCaptures(MerchantOrder order, JsonNode paypalOrder) {
        JsonNode units = paypalOrder.path("purchase_units");
        if (!units.isArray()) {
            return 0;
        }
        int stored = 0;
        for (JsonNode unit : units) {
            JsonNode captures = unit.path("payments").path("captures");
            if (!captures.isArray()) {
                continue;
            }
            for (JsonNode capture : captures) {
                String captureId = capture.path("id").asText();
                if (captureId.isBlank() || transactionRepository.findByCaptureId(captureId).isPresent()) {
                    continue;
                }
                String status = capture.path("status").asText("COMPLETED");
                String value = capture.path("amount").path("value").asText();
                String currency = capture.path("amount").path("currency_code").asText();
                if (value.isBlank() || currency.isBlank()) {
                    continue;
                }
                LocalDateTime capturedAt = null;
                String createTime = capture.path("create_time").asText();
                if (!createTime.isBlank()) {
                    capturedAt = OffsetDateTime.parse(createTime).toLocalDateTime();
                }
                transactionRepository.save(new PayPalTransaction(
                        order.getPaypalOrderId(),
                        captureId,
                        order.getMerchantOrderId(),
                        new BigDecimal(value),
                        currency,
                        status,
                        capturedAt
                ));
                stored++;
            }
        }
        return stored;
    }

    private void searchRecentTransactions() {
        OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = end.minusDays(2);
        DateTimeFormatter fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        try {
            JsonNode response = paypalClient.searchTransactions(
                    start.format(fmt),
                    end.format(fmt)
            );
            if (response != null) {
                log.info("Transaction Search returned {} details (up to 3h lag).",
                        response.path("transaction_details").size());
            }
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Transaction Search unavailable (enable it on the sandbox app). HTTP {}: {}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );
        } catch (RuntimeException ex) {
            log.warn("Transaction Search skipped: {}", ex.getMessage());
        }
    }
}
