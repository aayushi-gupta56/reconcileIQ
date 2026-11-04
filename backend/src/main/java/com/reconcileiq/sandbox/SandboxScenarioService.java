package com.reconcileiq.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.reconcileiq.config.AppMetadata;
import com.reconcileiq.config.AppMetadataRepository;
import com.reconcileiq.ledger.LedgerEntry;
import com.reconcileiq.ledger.LedgerEntryRepository;
import com.reconcileiq.orders.MerchantOrder;
import com.reconcileiq.orders.MerchantOrderRepository;
import com.reconcileiq.paypal.PayPalCaptureRecorder;
import com.reconcileiq.paypal.PayPalCaptureRecorder.CaptureRecord;
import com.reconcileiq.paypal.PayPalClient;
import com.reconcileiq.paypal.PayPalClient.PayPalOrderResult;
import com.reconcileiq.paypal.PayPalTransaction;
import com.reconcileiq.paypal.PayPalTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Consumer;

@Service
public class SandboxScenarioService {

    public static final String MATCHED = "RIQ-MATCHED";
    public static final String AMOUNT = "RIQ-AMOUNT";
    public static final String CURRENCY = "RIQ-CURRENCY";
    public static final String MISSING = "RIQ-MISSING";
    public static final String UNEXPECTED = "RIQ-UNEXPECTED";
    public static final String REFERENCE = "RIQ-REF";
    public static final String REFERENCE_WRONG = "RIQ-REF-WRONG";
    public static final String DUPLICATE = "RIQ-DUP";

    private static final String UNEXPECTED_KEY = "sandbox.scenario.unexpected.paypal_order_id";
    private static final String DUP_B_KEY = "sandbox.scenario.duplicate.order_b_id";

    private static final Logger log = LoggerFactory.getLogger(SandboxScenarioService.class);

    private final PayPalClient paypalClient;
    private final PayPalCaptureRecorder captureRecorder;
    private final MerchantOrderRepository merchantOrderRepository;
    private final PayPalTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AppMetadataRepository metadataRepository;

    public SandboxScenarioService(
            PayPalClient paypalClient,
            PayPalCaptureRecorder captureRecorder,
            MerchantOrderRepository merchantOrderRepository,
            PayPalTransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AppMetadataRepository metadataRepository) {
        this.paypalClient = paypalClient;
        this.captureRecorder = captureRecorder;
        this.merchantOrderRepository = merchantOrderRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.metadataRepository = metadataRepository;
    }

    public ScenarioRow createMatched(Consumer<String> printer, Scanner scanner) {
        Optional<MerchantOrder> existing = merchantOrderRepository.findByMerchantOrderId(MATCHED);
        if (existing.isPresent()) {
            return skipped("MATCHED", existing.get());
        }
        ledger(MATCHED, "100.00", "USD", "Matched 100.00 USD");
        PayPalOrderResult order = createPayPal(MATCHED, "100.00", "USD", MATCHED, null, "riq-sandbox-matched");
        approve(printer, scanner, "MATCHED", order);
        CaptureRecord capture = capture(order.paypalOrderId());
        saveMerchant(MATCHED, "100.00", "USD", order);
        saveTx(capture, MATCHED, capture.paypalOrderId());
        return row("MATCHED", MATCHED, order.paypalOrderId(), capture.captureId(), "created");
    }

    public ScenarioRow createAmountMismatch(Consumer<String> printer, Scanner scanner) {
        Optional<MerchantOrder> existing = merchantOrderRepository.findByMerchantOrderId(AMOUNT);
        if (existing.isPresent()) {
            return skipped("AMOUNT_MISMATCH", existing.get());
        }
        ledger(AMOUNT, "100.00", "USD", "Ledger expects 100.00 USD");
        PayPalOrderResult order = createPayPal(AMOUNT, "90.00", "USD", AMOUNT, null, "riq-sandbox-amount");
        approve(printer, scanner, "AMOUNT_MISMATCH", order);
        CaptureRecord capture = capture(order.paypalOrderId());
        saveMerchant(AMOUNT, "100.00", "USD", order);
        saveTx(capture, AMOUNT, capture.paypalOrderId());
        return row("AMOUNT_MISMATCH", AMOUNT, order.paypalOrderId(), capture.captureId(), "created");
    }

    public ScenarioRow createCurrencyMismatch(Consumer<String> printer, Scanner scanner) {
        Optional<MerchantOrder> existing = merchantOrderRepository.findByMerchantOrderId(CURRENCY);
        if (existing.isPresent()) {
            return skipped("CURRENCY_MISMATCH", existing.get());
        }
        ledger(CURRENCY, "100.00", "USD", "Ledger expects USD");
        PayPalOrderResult order = createPayPal(CURRENCY, "100.00", "EUR", CURRENCY, null, "riq-sandbox-currency");
        approve(printer, scanner, "CURRENCY_MISMATCH", order);
        CaptureRecord capture = capture(order.paypalOrderId());
        saveMerchant(CURRENCY, "100.00", "USD", order);
        saveTx(capture, CURRENCY, capture.paypalOrderId());
        return row("CURRENCY_MISMATCH", CURRENCY, order.paypalOrderId(), capture.captureId(), "created");
    }

    public ScenarioRow createMissingPayment(Consumer<String> printer) {
        Optional<MerchantOrder> existing = merchantOrderRepository.findByMerchantOrderId(MISSING);
        if (existing.isPresent()) {
            return skipped("MISSING_PAYMENT", existing.get());
        }
        ledger(MISSING, "100.00", "USD", "Ledger only; no PayPal order");
        MerchantOrder order = new MerchantOrder(MISSING, new BigDecimal("100.00"), "USD");
        order.setStatus("AWAITING_PAYMENT");
        merchantOrderRepository.save(order);
        printer.accept("MISSING_PAYMENT: ledger " + MISSING + " stored with no PayPal order.");
        return new ScenarioRow("MISSING_PAYMENT", MISSING, "-", "-", "created");
    }

    public ScenarioRow createUnexpectedPayment(Consumer<String> printer, Scanner scanner) {
        Optional<AppMetadata> existing = metadataRepository.findByAppKey(UNEXPECTED_KEY);
        if (existing.isPresent()) {
            return new ScenarioRow(
                    "UNEXPECTED_PAYMENT",
                    "-",
                    existing.get().getAppValue(),
                    "-",
                    "skipped"
            );
        }
        PayPalOrderResult order = createPayPal(
                UNEXPECTED, "40.00", "USD", UNEXPECTED, null, "riq-sandbox-unexpected");
        approve(printer, scanner, "UNEXPECTED_PAYMENT", order);
        CaptureRecord capture = capture(order.paypalOrderId());
        saveTx(capture, null, capture.paypalOrderId());
        metadataRepository.save(new AppMetadata(UNEXPECTED_KEY, order.paypalOrderId()));
        return row("UNEXPECTED_PAYMENT", "-", order.paypalOrderId(), capture.captureId(), "created");
    }

    public ScenarioRow createReferenceMismatch(Consumer<String> printer, Scanner scanner) {
        Optional<MerchantOrder> existing = merchantOrderRepository.findByMerchantOrderId(REFERENCE);
        if (existing.isPresent()) {
            return skipped("REFERENCE_MISMATCH", existing.get());
        }
        ledger(REFERENCE, "100.00", "USD", "Ledger merchant id RIQ-REF");
        PayPalOrderResult order = createPayPal(
                REFERENCE, "100.00", "USD", REFERENCE_WRONG, REFERENCE_WRONG, "riq-sandbox-ref");
        approve(printer, scanner, "REFERENCE_MISMATCH", order);
        CaptureRecord capture = capture(order.paypalOrderId());
        saveMerchant(REFERENCE, "100.00", "USD", order);
        saveTx(capture, REFERENCE_WRONG, capture.paypalOrderId());
        return row("REFERENCE_MISMATCH", REFERENCE, order.paypalOrderId(), capture.captureId(), "created");
    }

    public List<ScenarioRow> createDuplicate(Consumer<String> printer, Scanner scanner) {
        Optional<MerchantOrder> existing = merchantOrderRepository.findByMerchantOrderId(DUPLICATE);
        if (existing.isPresent()) {
            String orderB = metadataRepository.findByAppKey(DUP_B_KEY).map(AppMetadata::getAppValue).orElse("-");
            return List.of(
                    skipped("DUPLICATE_PAYMENT", existing.get()),
                    new ScenarioRow("DUPLICATE_PAYMENT", DUPLICATE, orderB, "-", "skipped")
            );
        }
        ledger(DUPLICATE, "75.00", "USD", "Two checkout-retry PayPal orders");
        PayPalOrderResult orderA = createPayPal(
                DUPLICATE, "75.00", "USD", DUPLICATE, null, "riq-sandbox-dup-a");
        PayPalOrderResult orderB = createPayPal(
                DUPLICATE + "-B", "75.00", "USD", DUPLICATE, null, "riq-sandbox-dup-b");
        approve(printer, scanner, "DUPLICATE_PAYMENT A", orderA);
        approve(printer, scanner, "DUPLICATE_PAYMENT B", orderB);
        CaptureRecord captureA = capture(orderA.paypalOrderId());
        CaptureRecord captureB = capture(orderB.paypalOrderId());
        saveMerchant(DUPLICATE, "75.00", "USD", orderA);
        // Both captures share the merchant order's PayPal order id locally so the
        // existing rule engine classifies DUPLICATE_PAYMENT (matching references).
        saveTx(captureA, DUPLICATE, orderA.paypalOrderId());
        saveTx(new CaptureRecord(
                orderA.paypalOrderId(),
                captureB.captureId(),
                captureB.customId(),
                captureB.amount(),
                captureB.currency(),
                captureB.status(),
                captureB.capturedAt()
        ), DUPLICATE, orderA.paypalOrderId());
        metadataRepository.save(new AppMetadata(DUP_B_KEY, orderB.paypalOrderId()));
        return List.of(
                row("DUPLICATE_PAYMENT", DUPLICATE, orderA.paypalOrderId(), captureA.captureId(), "created"),
                row("DUPLICATE_PAYMENT", DUPLICATE, orderB.paypalOrderId(), captureB.captureId(), "created")
        );
    }

    private PayPalOrderResult createPayPal(
            String merchantOrderId,
            String amount,
            String currency,
            String customId,
            String invoiceId,
            String requestId) {
        return paypalClient.createOrder(
                merchantOrderId,
                new BigDecimal(amount),
                currency,
                customId,
                invoiceId,
                requestId
        );
    }

    private CaptureRecord capture(String paypalOrderId) {
        JsonNode response = paypalClient.captureOrder(paypalOrderId);
        CaptureRecord record = captureRecorder.firstCapture(paypalOrderId, response);
        if (!"COMPLETED".equalsIgnoreCase(record.status())) {
            throw new IllegalStateException("Capture is not completed: " + record.status());
        }
        return record;
    }

    private void saveMerchant(String merchantOrderId, String amount, String currency, PayPalOrderResult paypal) {
        MerchantOrder order = new MerchantOrder(merchantOrderId, new BigDecimal(amount), currency);
        order.setPaypalOrderId(paypal.paypalOrderId());
        order.setApprovalUrl(paypal.approvalUrl());
        order.setStatus("COMPLETED");
        merchantOrderRepository.save(order);
    }

    private void saveTx(CaptureRecord capture, String merchantOrderId, String paypalOrderId) {
        if (transactionRepository.findByCaptureId(capture.captureId()).isPresent()) {
            return;
        }
        transactionRepository.save(new PayPalTransaction(
                paypalOrderId,
                capture.captureId(),
                merchantOrderId,
                capture.amount(),
                capture.currency(),
                capture.status(),
                capture.capturedAt() == null ? LocalDateTime.now() : capture.capturedAt()
        ));
    }

    private void ledger(String merchantOrderId, String amount, String currency, String description) {
        ledgerEntryRepository.save(new LedgerEntry(
                merchantOrderId,
                merchantOrderId,
                new BigDecimal(amount),
                currency,
                "OPEN",
                LocalDateTime.now(),
                description
        ));
    }

    private void approve(Consumer<String> printer, Scanner scanner, String label, PayPalOrderResult order) {
        printer.accept("");
        printer.accept("Approve " + label + " in the sandbox buyer account:");
        printer.accept("  PayPal order: " + order.paypalOrderId());
        printer.accept("  " + order.approvalUrl());
        printer.accept("Press Enter after the buyer has approved the order...");
        if (scanner != null) {
            scanner.nextLine();
        }
    }

    private ScenarioRow skipped(String scenario, MerchantOrder order) {
        return new ScenarioRow(
                scenario,
                order.getMerchantOrderId(),
                order.getPaypalOrderId() == null ? "-" : order.getPaypalOrderId(),
                "-",
                "skipped"
        );
    }

    private static ScenarioRow row(
            String scenario,
            String merchantOrderId,
            String paypalOrderId,
            String captureId,
            String status) {
        return new ScenarioRow(scenario, merchantOrderId, paypalOrderId, captureId, status);
    }

    public record ScenarioRow(
            String scenario,
            String merchantOrderId,
            String paypalOrderId,
            String captureId,
            String status
    ) {}
}
