package com.reconcileiq.demo;

import com.reconcileiq.config.DataSourceMode;
import com.reconcileiq.config.ReconcileIqProperties;
import com.reconcileiq.orders.MerchantOrder;
import com.reconcileiq.orders.MerchantOrderRepository;
import com.reconcileiq.paypal.PayPalTransaction;
import com.reconcileiq.paypal.PayPalTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Seeds one local scenario per reconciliation status so the app runs
 * without PayPal credentials in DEMO_SCENARIOS mode.
 */
@Component
public class DemoScenarioSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoScenarioSeeder.class);
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 10, 1, 12, 0, 0);

    private final ReconcileIqProperties properties;
    private final MerchantOrderRepository merchantOrderRepository;
    private final PayPalTransactionRepository paypalTransactionRepository;

    public DemoScenarioSeeder(
            ReconcileIqProperties properties,
            MerchantOrderRepository merchantOrderRepository,
            PayPalTransactionRepository paypalTransactionRepository) {
        this.properties = properties;
        this.merchantOrderRepository = merchantOrderRepository;
        this.paypalTransactionRepository = paypalTransactionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.getDataSource() != DataSourceMode.DEMO_SCENARIOS) {
            log.info("Skipping demo scenario seed; data source is {}", properties.getDataSource());
            return;
        }

        seedMatched();
        seedAmountMismatch();
        seedCurrencyMismatch();
        seedMissingPayment();
        seedUnexpectedPayment();
        seedReferenceMismatch();
        seedDuplicatePayment();
        log.info("Demo scenarios are present (idempotent seed).");
    }

    /**
     * True when a reconciliation record belongs to the seeded demo scenarios.
     * Real orders use ORD-... ids, so origin (not the active mode) decides the tab.
     */
    public static boolean isDemoRecord(
            String merchantOrderId, String paypalOrderId, String captureId) {
        return startsWith(merchantOrderId, "DEMO-")
                || startsWith(paypalOrderId, "PP-DEMO-")
                || startsWith(captureId, "CAP-DEMO-");
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private void seedMatched() {
        upsertOrder("DEMO-MATCHED", "100.00", "USD", "PP-DEMO-MATCHED");
        upsertCapture("PP-DEMO-MATCHED", "CAP-DEMO-MATCHED", "DEMO-MATCHED",
                "100.00", "USD", BASE_TIME);
    }

    private void seedAmountMismatch() {
        upsertOrder("DEMO-AMOUNT", "100.00", "USD", "PP-DEMO-AMOUNT");
        upsertCapture("PP-DEMO-AMOUNT", "CAP-DEMO-AMOUNT", "DEMO-AMOUNT",
                "90.00", "USD", BASE_TIME.plusMinutes(1));
    }

    private void seedCurrencyMismatch() {
        upsertOrder("DEMO-CURRENCY", "100.00", "USD", "PP-DEMO-CURRENCY");
        upsertCapture("PP-DEMO-CURRENCY", "CAP-DEMO-CURRENCY", "DEMO-CURRENCY",
                "100.00", "EUR", BASE_TIME.plusMinutes(2));
    }

    private void seedMissingPayment() {
        upsertOrder("DEMO-MISSING", "100.00", "USD", "PP-DEMO-MISSING");
    }

    private void seedUnexpectedPayment() {
        upsertCapture("PP-DEMO-UNEXPECTED", "CAP-DEMO-UNEXPECTED", null,
                "40.00", "USD", BASE_TIME.plusMinutes(3));
    }

    private void seedReferenceMismatch() {
        upsertOrder("DEMO-REF", "100.00", "USD", "PP-DEMO-REF");
        upsertCapture("PP-DEMO-REF", "CAP-DEMO-REF", "DEMO-REF-OTHER",
                "100.00", "USD", BASE_TIME.plusMinutes(4));
    }

    private void seedDuplicatePayment() {
        upsertOrder("DEMO-DUP", "75.00", "USD", "PP-DEMO-DUP");
        upsertCapture("PP-DEMO-DUP", "CAP-DEMO-DUP-A", "DEMO-DUP",
                "75.00", "USD", BASE_TIME.plusMinutes(5));
        upsertCapture("PP-DEMO-DUP", "CAP-DEMO-DUP-B", "DEMO-DUP",
                "75.00", "USD", BASE_TIME.plusMinutes(5).plusSeconds(60));
    }

    private void upsertOrder(
            String merchantOrderId,
            String amount,
            String currency,
            String paypalOrderId) {
        merchantOrderRepository.findByMerchantOrderId(merchantOrderId)
                .ifPresentOrElse(
                        existing -> { },
                        () -> {
                            MerchantOrder order = new MerchantOrder(
                                    merchantOrderId,
                                    new BigDecimal(amount),
                                    currency);
                            order.setPaypalOrderId(paypalOrderId);
                            order.setStatus("COMPLETED");
                            merchantOrderRepository.save(order);
                        });
    }

    private void upsertCapture(
            String paypalOrderId,
            String captureId,
            String merchantOrderId,
            String amount,
            String currency,
            LocalDateTime capturedAt) {
        if (paypalTransactionRepository.findByCaptureId(captureId).isPresent()) {
            return;
        }
        paypalTransactionRepository.save(new PayPalTransaction(
                paypalOrderId,
                captureId,
                merchantOrderId,
                new BigDecimal(amount),
                currency,
                "COMPLETED",
                capturedAt
        ));
    }
}
