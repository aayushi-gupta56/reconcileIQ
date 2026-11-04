package com.reconcileiq.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcileiq.reconciliation.ReconciliationResult;
import com.reconcileiq.reconciliation.ReconciliationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvestigationOutputValidatorTest {

    private final InvestigationOutputValidator validator = new InvestigationOutputValidator(new ObjectMapper());

    @Test
    void rejectsMalformedJson() {
        var attempt = validator.parseAndValidate("not json", amountResult(), amountEvidence());
        assertThat(attempt.ok()).isFalse();
        assertThat(attempt.violations()).anyMatch(v -> v.contains("JSON"));
    }

    @Test
    void stripsThinkBlocksAndFences() {
        String raw = """
                <think>scratch</think>
                ```json
                {"summary":"Order DEMO-AMOUNT expected 100.00 USD but the matched capture shows 90.00 USD.",
                "possibleExplanations":["Root cause cannot be determined from the available evidence."],
                "recommendedChecks":["Confirm the order total in the merchant system","Open capture CAP-DEMO-AMOUNT in PayPal","Check webhook logs for this order"],
                "requiresHumanReview":false}
                ```
                """;
        var attempt = validator.parseAndValidate(raw, amountResult(), amountEvidence());
        assertThat(attempt.ok()).isTrue();
        assertThat(attempt.value().requiresHumanReview()).isTrue();
    }

    @Test
    void rejectsInventedExampleIds() {
        String raw = """
                {"summary":"Order A1 expected 100.00 USD.",
                "possibleExplanations":["A fee may have been applied."],
                "recommendedChecks":["Look up P1","Open C2","Check logs"],
                "requiresHumanReview":true}
                """;
        var attempt = validator.parseAndValidate(raw, amountResult(), amountEvidence());
        assertThat(attempt.ok()).isFalse();
        assertThat(attempt.violations()).anyMatch(v -> v.contains("Invented"));
    }

    @Test
    void rejectsRefundRecommendation() {
        String raw = """
                {"summary":"Order DEMO-AMOUNT expected 100.00 USD but capture CAP-DEMO-AMOUNT shows 90.00 USD.",
                "possibleExplanations":["Root cause cannot be determined from the available evidence."],
                "recommendedChecks":["Refund the customer 10.00 USD","Open CAP-DEMO-AMOUNT in PayPal","Check merchant logs"],
                "requiresHumanReview":true}
                """;
        var attempt = validator.parseAndValidate(raw, amountResult(), amountEvidence());
        assertThat(attempt.ok()).isFalse();
        assertThat(attempt.violations()).anyMatch(v -> v.toLowerCase().contains("refund"));
    }

    @Test
    void rejectsEvidenceOnlyChecks() {
        String raw = """
                {"summary":"Order DEMO-AMOUNT expected 100.00 USD but capture CAP-DEMO-AMOUNT shows 90.00 USD.",
                "possibleExplanations":["Root cause cannot be determined from the available evidence."],
                "recommendedChecks":["List every capture ID","Compare timestamps and amounts","Check PayPal dashboard for DEMO-AMOUNT"],
                "requiresHumanReview":true}
                """;
        var attempt = validator.parseAndValidate(raw, amountResult(), amountEvidence());
        assertThat(attempt.ok()).isFalse();
        assertThat(attempt.violations()).anyMatch(v -> v.contains("evidence-only"));
    }

    private static ReconciliationResult amountResult() {
        return ObservedFactsBuilderTest.result(
                ReconciliationStatus.AMOUNT_MISMATCH,
                "DEMO-AMOUNT",
                "PP-DEMO-AMOUNT",
                "CAP-DEMO-AMOUNT",
                "100.00",
                "90.00",
                "USD",
                "USD",
                1
        );
    }

    private static List<InvestigationEvidence> amountEvidence() {
        return List.of(ObservedFactsBuilderTest.evidence(
                "CAP-DEMO-AMOUNT", "PP-DEMO-AMOUNT", "DEMO-AMOUNT", "90.00", "USD",
                LocalDateTime.of(2026, 10, 1, 12, 0)
        ));
    }
}
