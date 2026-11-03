package com.reconcileiq.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcileiq.ai.InvestigationEvidence;
import com.reconcileiq.ai.InvestigationFallbackFactory;
import com.reconcileiq.ai.InvestigationOutputValidator;
import com.reconcileiq.ai.InvestigationPromptBuilder;
import com.reconcileiq.ai.InvestigationService;
import com.reconcileiq.ai.ObservedFactsBuilder;
import com.reconcileiq.ai.ValidatedInvestigation;
import com.reconcileiq.ai.ValidationStatus;
import com.reconcileiq.config.ReconcileIqProperties;
import com.reconcileiq.reconciliation.ReconciliationResult;
import com.reconcileiq.reconciliation.ReconciliationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationRunnerTest {

    @Test
    void writesEvaluationTable() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObservedFactsBuilder factsBuilder = new ObservedFactsBuilder();
        InvestigationOutputValidator validator = new InvestigationOutputValidator(mapper);
        InvestigationFallbackFactory fallbackFactory = new InvestigationFallbackFactory(factsBuilder);
        InvestigationPromptBuilder promptBuilder = new InvestigationPromptBuilder(factsBuilder);

        List<Path> files = Files.list(Path.of("src/test/resources/eval-cases"))
                .filter(path -> path.toString().endsWith(".json"))
                .sorted()
                .toList();
        if (files.isEmpty()) {
            var url = EvaluationRunnerTest.class.getResource("/eval-cases");
            assertThat(url).isNotNull();
            files = Files.list(Path.of(url.toURI()))
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }

        List<String> rows = new ArrayList<>();
        rows.add("| Case | Status | JSON valid | Invented IDs | Banned phrases | Repeats evidence | Fallback used | Latency ms |");
        rows.add("|---|---|---|---|---|---|---|---|");

        for (Path file : files) {
            EvalCase evalCase = mapper.readValue(file.toFile(), EvalCase.class);
            ReconciliationResult result = toResult(evalCase);
            List<InvestigationEvidence> evidence = toEvidence(evalCase);
            List<String> facts = factsBuilder.build(result, evidence);
            assertThat(facts).isNotEmpty();
            assertThat(factsBuilder.build(result, evidence)).isEqualTo(facts);

            InvestigationService service = new InvestigationService(
                    null, null, null,
                    (system, user) -> evalCase.modelOutput(),
                    promptBuilder,
                    factsBuilder,
                    validator,
                    fallbackFactory,
                    new ReconcileIqProperties()
            );
            long start = System.currentTimeMillis();
            ValidatedInvestigation out = service.runModel(
                    promptBuilder.build(result, evidence),
                    result,
                    evidence,
                    facts
            );
            long latency = System.currentTimeMillis() - start;

            InvestigationOutputValidator.ParseAttempt parsed =
                    validator.parseAndValidate(evalCase.modelOutput(), result, evidence);
            boolean jsonValid = InvestigationOutputValidator.extractJson(evalCase.modelOutput()) != null
                    && parsed.violations().stream().noneMatch(v -> v.contains("JSON"));
            Set<String> allowed = validator.allowedIds(result, evidence);
            List<String> invented = InvestigationOutputValidator.inventedIds(
                    evalCase.modelOutput() == null ? "" : evalCase.modelOutput(), allowed);
            boolean banned = validator.hasBannedPhrase(evalCase.modelOutput());
            boolean repeats = validator.repeatsEvidence(
                    parsed.value() == null ? List.of() : parsed.value().recommendedChecks());
            if (!jsonValid) {
                repeats = false;
            }
            boolean fallback = out.validationStatus() == ValidationStatus.FALLBACK;

            rows.add(String.format(
                    "| %s | %s | %s | %d | %s | %s | %s | %d |",
                    evalCase.id(),
                    evalCase.status(),
                    jsonValid ? "yes" : "no",
                    invented.size(),
                    banned ? "yes" : "no",
                    repeats ? "yes" : "no",
                    fallback ? "yes" : "no",
                    latency
            ));
        }

        String table = """
                # Investigation evaluation

                Stub-model run of `src/test/resources/eval-cases/` through Java facts, the prompt builder,
                the output validator (one retry) and the deterministic fallback.
                Temperature for live runs is `0.1`. Re-run with:

                ```bash
                mvn -f backend/pom.xml -Dtest=EvaluationRunnerTest test
                ```

                """ + String.join("\n", rows) + "\n";

        Path moduleDocs = Path.of("target/EVALUATION.md");
        Files.createDirectories(moduleDocs.getParent());
        Files.writeString(moduleDocs, table);

        Path repoDocs = Path.of("..", "docs", "EVALUATION.md");
        if (!Files.isDirectory(repoDocs.getParent())) {
            repoDocs = Path.of("docs", "EVALUATION.md");
        }
        Files.createDirectories(repoDocs.getParent());
        Files.writeString(repoDocs, table);

        assertThat(files.size()).isGreaterThanOrEqualTo(12);
        assertThat(table).contains("duplicate-60s");
    }

    private static ReconciliationResult toResult(EvalCase evalCase) {
        ReconciliationResult result = new ReconciliationResult();
        result.setStatus(ReconciliationStatus.valueOf(evalCase.status()));
        result.setMerchantOrderId(evalCase.merchantOrderId());
        result.setPaypalOrderId(evalCase.paypalOrderId());
        result.setCaptureId(evalCase.captureId());
        result.setExpectedAmount(decimal(evalCase.expectedAmount()));
        result.setActualAmount(decimal(evalCase.actualAmount()));
        result.setExpectedCurrency(evalCase.expectedCurrency());
        result.setActualCurrency(evalCase.actualCurrency());
        result.setMatchedTransactionCount(
                evalCase.matchedTransactionCount() == null ? 0 : evalCase.matchedTransactionCount());
        result.setExplanation(evalCase.explanation());
        return result;
    }

    private static List<InvestigationEvidence> toEvidence(EvalCase evalCase) {
        if (evalCase.evidence() == null) {
            return List.of();
        }
        return evalCase.evidence().stream()
                .map(item -> new InvestigationEvidence(
                        item.captureId(),
                        item.paypalOrderId(),
                        item.merchantOrderId(),
                        decimal(item.amount()),
                        item.currency(),
                        item.status(),
                        item.capturedAt() == null ? null : LocalDateTime.parse(item.capturedAt())
                ))
                .toList();
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}
