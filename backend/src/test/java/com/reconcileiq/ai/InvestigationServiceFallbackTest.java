package com.reconcileiq.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcileiq.config.ReconcileIqProperties;
import com.reconcileiq.reconciliation.ReconciliationResult;
import com.reconcileiq.reconciliation.ReconciliationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvestigationServiceFallbackTest {

    @Test
    void timeoutUsesFallback() {
        ObservedFactsBuilder factsBuilder = new ObservedFactsBuilder();
        InvestigationService service = new InvestigationService(
                null, null, null,
                (system, user) -> {
                    throw new IllegalStateException("AI request timed out");
                },
                new InvestigationPromptBuilder(factsBuilder),
                factsBuilder,
                new InvestigationOutputValidator(new ObjectMapper()),
                new InvestigationFallbackFactory(factsBuilder),
                new ReconcileIqProperties()
        );
        ReconciliationResult result = ObservedFactsBuilderTest.result(
                ReconciliationStatus.AMOUNT_MISMATCH,
                "DEMO-AMOUNT", "PP-DEMO-AMOUNT", "CAP-DEMO-AMOUNT",
                "100.00", "90.00", "USD", "USD", 1);
        List<InvestigationEvidence> evidence = List.of(
                ObservedFactsBuilderTest.evidence(
                        "CAP-DEMO-AMOUNT", "PP-DEMO-AMOUNT", "DEMO-AMOUNT", "90.00", "USD",
                        LocalDateTime.of(2026, 10, 1, 12, 0))
        );
        List<String> facts = factsBuilder.build(result, evidence);
        ValidatedInvestigation out = service.runModel(
                new InvestigationPromptBuilder(factsBuilder).build(result, evidence),
                result, evidence, facts);
        assertThat(out.validationStatus()).isEqualTo(ValidationStatus.FALLBACK);
        assertThat(out.recommendedChecks()).hasSize(4);
        assertThat(out.requiresHumanReview()).isTrue();
    }

    @Test
    void retryThenFallbackOnInventedIds() {
        ObservedFactsBuilder factsBuilder = new ObservedFactsBuilder();
        AiInvestigationClient client = (system, user) -> """
                {"summary":"Order A1 is wrong.",
                "possibleExplanations":["Fees may apply."],
                "recommendedChecks":["Look up A1","Open P1","Check C2"],
                "requiresHumanReview":true}
                """;
        InvestigationService service = new InvestigationService(
                null, null, null, client,
                new InvestigationPromptBuilder(factsBuilder),
                factsBuilder,
                new InvestigationOutputValidator(new ObjectMapper()),
                new InvestigationFallbackFactory(factsBuilder),
                new ReconcileIqProperties()
        );
        ReconciliationResult result = ObservedFactsBuilderTest.result(
                ReconciliationStatus.AMOUNT_MISMATCH,
                "DEMO-AMOUNT", "PP-DEMO-AMOUNT", "CAP-DEMO-AMOUNT",
                "100.00", "90.00", "USD", "USD", 1);
        List<InvestigationEvidence> evidence = List.of(
                ObservedFactsBuilderTest.evidence(
                        "CAP-DEMO-AMOUNT", "PP-DEMO-AMOUNT", "DEMO-AMOUNT", "90.00", "USD",
                        LocalDateTime.of(2026, 10, 1, 12, 0))
        );
        ValidatedInvestigation out = service.runModel(
                new InvestigationPromptBuilder(factsBuilder).build(result, evidence),
                result, evidence, factsBuilder.build(result, evidence));
        assertThat(out.validationStatus()).isEqualTo(ValidationStatus.FALLBACK);
    }
}
