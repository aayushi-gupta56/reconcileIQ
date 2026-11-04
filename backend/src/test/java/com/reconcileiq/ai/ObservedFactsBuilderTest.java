package com.reconcileiq.ai;

import com.reconcileiq.reconciliation.ReconciliationResult;
import com.reconcileiq.reconciliation.ReconciliationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObservedFactsBuilderTest {

    private final ObservedFactsBuilder builder = new ObservedFactsBuilder();

    @Test
    void duplicateFactsAreDeterministic() {
        ReconciliationResult result = result(ReconciliationStatus.DUPLICATE_PAYMENT, "DEMO-DUP", "PP-DEMO-DUP", null,
                "75.00", "150.00", "USD", "USD", 2);
        List<InvestigationEvidence> evidence = List.of(
                evidence("CAP-DEMO-DUP-A", "PP-DEMO-DUP", "DEMO-DUP", "75.00", "USD", LocalDateTime.of(2026, 10, 1, 12, 5, 0)),
                evidence("CAP-DEMO-DUP-B", "PP-DEMO-DUP", "DEMO-DUP", "75.00", "USD", LocalDateTime.of(2026, 10, 1, 12, 6, 0))
        );
        List<String> facts = builder.build(result, evidence);
        assertThat(facts).containsExactly(
                "Matched transaction count is 2",
                "Capture IDs are CAP-DEMO-DUP-A and CAP-DEMO-DUP-B",
                "Both transactions are 75.00 USD",
                "Captures are 60 seconds apart"
        );
        assertThat(builder.build(result, evidence)).isEqualTo(facts);
    }

    @Test
    void amountMismatchFacts() {
        ReconciliationResult result = result(ReconciliationStatus.AMOUNT_MISMATCH, "DEMO-AMOUNT", "PP-DEMO-AMOUNT",
                "CAP-DEMO-AMOUNT", "100.00", "90.00", "USD", "USD", 1);
        List<String> facts = builder.build(result, List.of(
                evidence("CAP-DEMO-AMOUNT", "PP-DEMO-AMOUNT", "DEMO-AMOUNT", "90.00", "USD", LocalDateTime.now())
        ));
        assertThat(facts).contains(
                "Expected amount is 100.00 USD",
                "Actual amount is 90.00 USD",
                "Difference is -10.00",
                "Currencies match",
                "Capture ID is CAP-DEMO-AMOUNT"
        );
    }

    @Test
    void currencyMismatchFacts() {
        ReconciliationResult result = result(ReconciliationStatus.CURRENCY_MISMATCH, "DEMO-CURRENCY", "PP-DEMO-CURRENCY",
                "CAP-DEMO-CURRENCY", "100.00", "100.00", "USD", "EUR", 1);
        List<String> facts = builder.build(result, List.of(
                evidence("CAP-DEMO-CURRENCY", "PP-DEMO-CURRENCY", "DEMO-CURRENCY", "100.00", "EUR", LocalDateTime.now())
        ));
        assertThat(facts).contains(
                "Expected currency is USD",
                "Actual currency is EUR",
                "Both amounts are 100.00",
                "Capture ID is CAP-DEMO-CURRENCY"
        );
    }

    @Test
    void missingPaymentFacts() {
        ReconciliationResult result = result(ReconciliationStatus.MISSING_PAYMENT, "DEMO-MISSING", "PP-DEMO-MISSING",
                null, "100.00", null, "USD", null, 0);
        List<String> facts = builder.build(result, List.of());
        assertThat(facts).containsExactly(
                "Merchant order DEMO-MISSING has PayPal order ID PP-DEMO-MISSING",
                "Matched transaction count is 0"
        );
    }

    @Test
    void unexpectedPaymentFacts() {
        ReconciliationResult result = result(ReconciliationStatus.UNEXPECTED_PAYMENT, null, "PP-DEMO-UNEXPECTED",
                "CAP-DEMO-UNEXPECTED", null, "40.00", null, "USD", 1);
        List<String> facts = builder.build(result, List.of(
                evidence("CAP-DEMO-UNEXPECTED", "PP-DEMO-UNEXPECTED", null, "40.00", "USD", LocalDateTime.now())
        ));
        assertThat(facts).contains(
                "PayPal order ID is PP-DEMO-UNEXPECTED",
                "Capture ID is CAP-DEMO-UNEXPECTED",
                "No merchant order ID is present in the dataset"
        );
    }

    @Test
    void referenceMismatchFacts() {
        ReconciliationResult result = result(ReconciliationStatus.REFERENCE_MISMATCH, "DEMO-REF", "PP-DEMO-REF",
                "CAP-DEMO-REF", "100.00", "100.00", "USD", "USD", 1);
        List<String> facts = builder.build(result, List.of(
                evidence("CAP-DEMO-REF", "PP-DEMO-REF", "DEMO-REF-OTHER", "100.00", "USD", LocalDateTime.now())
        ));
        assertThat(facts).contains(
                "Merchant order ID is DEMO-REF",
                "PayPal order ID is PP-DEMO-REF",
                "Payment merchant reference is DEMO-REF-OTHER",
                "Capture ID is CAP-DEMO-REF"
        );
    }

    @Test
    void matchedFacts() {
        ReconciliationResult result = result(ReconciliationStatus.MATCHED, "DEMO-MATCHED", "PP-DEMO-MATCHED",
                "CAP-DEMO-MATCHED", "100.00", "100.00", "USD", "USD", 1);
        assertThat(builder.build(result, List.of(
                evidence("CAP-DEMO-MATCHED", "PP-DEMO-MATCHED", "DEMO-MATCHED", "100.00", "USD", LocalDateTime.now())
        ))).contains("Capture ID is CAP-DEMO-MATCHED");
    }

    @Test
    void captureIdsFallBackToEvidence() {
        ReconciliationResult result = result(ReconciliationStatus.DUPLICATE_PAYMENT, "X", "P", null,
                "10.00", "20.00", "USD", "USD", 2);
        String ids = builder.captureIds(result, List.of(
                evidence("C-ONE", "P", "X", "10.00", "USD", LocalDateTime.now()),
                evidence("C-TWO", "P", "X", "10.00", "USD", LocalDateTime.now())
        ));
        assertThat(ids).isEqualTo("C-ONE, C-TWO");
    }

    static ReconciliationResult result(
            ReconciliationStatus status,
            String merchant,
            String paypal,
            String capture,
            String expected,
            String actual,
            String expectedCurrency,
            String actualCurrency,
            int matched
    ) {
        ReconciliationResult result = new ReconciliationResult();
        result.setStatus(status);
        result.setMerchantOrderId(merchant);
        result.setPaypalOrderId(paypal);
        result.setCaptureId(capture);
        result.setExpectedAmount(expected == null ? null : new BigDecimal(expected));
        result.setActualAmount(actual == null ? null : new BigDecimal(actual));
        result.setExpectedCurrency(expectedCurrency);
        result.setActualCurrency(actualCurrency);
        result.setMatchedTransactionCount(matched);
        result.setExplanation(status.name());
        return result;
    }

    static InvestigationEvidence evidence(
            String captureId,
            String paypalOrderId,
            String merchantOrderId,
            String amount,
            String currency,
            LocalDateTime capturedAt
    ) {
        return new InvestigationEvidence(
                captureId,
                paypalOrderId,
                merchantOrderId,
                amount == null ? null : new BigDecimal(amount),
                currency,
                "COMPLETED",
                capturedAt
        );
    }
}
