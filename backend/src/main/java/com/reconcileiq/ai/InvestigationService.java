package com.reconcileiq.ai;

import com.reconcileiq.reconciliation.ReconciliationResult;
import com.reconcileiq.reconciliation.ReconciliationResultRepository;
import com.reconcileiq.reconciliation.ReconciliationStatus;
import com.reconcileiq.reconciliation.ReconciliationResultTransactionRepository;
import com.reconcileiq.paypal.PayPalTransaction;
import com.reconcileiq.reconciliation.ReconciliationResultTransaction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class InvestigationService {

    private static final String UNDETERMINED =
            "Root cause cannot be determined from the available evidence.";

    private static final String FALLBACK_CHECK =
            "Verify the underlying payment records manually.";

    /**
     * The model no longer produces a confidence value (self-reported LLM
     * confidence is not meaningful). null = "not applicable".
     * If your column is NOT NULL, either make it nullable or change this constant.
     */
    private static final BigDecimal CONFIDENCE_NOT_APPLICABLE = BigDecimal.ZERO;

    // Blocks recommendations that tell the analyst to change money movement.
    // "Check the capture record" is still allowed.
    private static final Pattern FORBIDDEN_ACTION = Pattern.compile(
            "(?i)\\b(refund|void|chargeback|reverse|re-?capture)\\b"
                    + "|\\b(issue|initiate|process|trigger)\\b.*\\b(capture|payment)\\b"
                    + "|\\bcapture (the )?(payment|funds)\\b");

    private final ReconciliationResultRepository resultRepository;
    private final InvestigationReportRepository reportRepository;
    private final AiInvestigationClient aiClient;
    private final InvestigationPromptBuilder promptBuilder;
    private final String modelName;
    private final ReconciliationResultTransactionRepository
        resultTransactionRepository;

    public InvestigationService(
            ReconciliationResultRepository resultRepository,
            InvestigationReportRepository reportRepository,
            AiInvestigationClient aiClient,
            InvestigationPromptBuilder promptBuilder,
            @Value("${ai.ollama.model}") String modelName,
            ReconciliationResultTransactionRepository resultTransactionRepository
    ) {
        this.resultRepository = resultRepository;
        this.reportRepository = reportRepository;
        this.aiClient = aiClient;
        this.promptBuilder = promptBuilder;
        this.modelName = modelName;
        this.resultTransactionRepository = resultTransactionRepository;
    }

    @Transactional
    public InvestigationReport investigate(Long resultId) {

        ReconciliationResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reconciliation result not found: " + resultId));

        if (result.getStatus() == ReconciliationStatus.MATCHED) {
            throw new IllegalArgumentException(
                    "Matched transactions do not require investigation");
        }

        List<ReconciliationResultTransaction> associations =
        resultTransactionRepository
                .findAllByReconciliationResultId(result.getId());

List<PayPalTransaction> transactions =
        associations.stream()
                .map(ReconciliationResultTransaction::getPaypalTransaction)
                .toList();

                List<InvestigationEvidence> evidence = transactions.stream()
                .map(tx -> new InvestigationEvidence(
                        tx.getCaptureId(),
                        tx.getPaypalOrderId(),
                        tx.getMerchantOrderId(),
                        tx.getAmount(),
                        tx.getCurrency(),
                        tx.getStatus(),
                        tx.getCapturedAt()
                ))
                .toList();

        InvestigationPromptBuilder.Prompt prompt = promptBuilder.build(result, evidence);

        AiModelOutput output = aiClient.investigate(prompt.system(), prompt.user());

        System.out.println("InvestigationResponse");
        System.out.println("--------------------------------");
        System.out.println(output);
        System.out.println("--------------------------------");
        
        InvestigationResponse response = toResponse(output);

        return reportRepository.save(
                new InvestigationReport(result.getId(), response, modelName));
    }

    /**
     * Validates the model output in code (not just in the prompt) and maps it
     * into the existing InvestigationResponse shape.
     */
    private InvestigationResponse toResponse(AiModelOutput out) {
        if (out == null || out.summary() == null || out.summary().isBlank()) {
            throw new IllegalStateException("AI returned an empty or invalid investigation");
        }

        List<String> facts = clean(out.observedFacts());

        List<String> explanations = clean(out.possibleExplanations());
        if (explanations.isEmpty()) {
            explanations = List.of(UNDETERMINED);
        }

        List<String> checks = clean(out.recommendedChecks()).stream()
                .filter(c -> !FORBIDDEN_ACTION.matcher(c).find())
                .toList();
        if (checks.isEmpty()) {
            checks = List.of(FALLBACK_CHECK);
        }

        // Observed fact vs hypothesis stays visible inside likelyCause.
        String likelyCause = out.summary().trim()
                + "\n\nPossible explanations (unverified):\n"
                + bullets(explanations);

        String recommendedAction = numbered(checks);

        // requiresHumanReview is always true, whatever the model said.
        return new InvestigationResponse(
                likelyCause,
                CONFIDENCE_NOT_APPLICABLE,
                facts,
                recommendedAction,
                true
        );
    }

    private static List<String> clean(List<String> list) {
        return list == null ? List.of() : list.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();
    }

    private static String bullets(List<String> items) {
        return items.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
    }

    private static String numbered(List<String> items) {
        return IntStream.range(0, items.size())
                .mapToObj(i -> (i + 1) + ". " + items.get(i))
                .collect(Collectors.joining("\n"));
    }
}