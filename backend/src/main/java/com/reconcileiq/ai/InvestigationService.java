package com.reconcileiq.ai;

import com.reconcileiq.config.ReconcileIqProperties;
import com.reconcileiq.paypal.PayPalTransaction;
import com.reconcileiq.reconciliation.ReconciliationResult;
import com.reconcileiq.reconciliation.ReconciliationResultRepository;
import com.reconcileiq.reconciliation.ReconciliationResultTransaction;
import com.reconcileiq.reconciliation.ReconciliationResultTransactionRepository;
import com.reconcileiq.reconciliation.ReconciliationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);

    private final ReconciliationResultRepository resultRepository;
    private final InvestigationReportRepository reportRepository;
    private final ReconciliationResultTransactionRepository resultTransactionRepository;
    private final AiInvestigationClient aiClient;
    private final InvestigationPromptBuilder promptBuilder;
    private final ObservedFactsBuilder factsBuilder;
    private final InvestigationOutputValidator validator;
    private final InvestigationFallbackFactory fallbackFactory;
    private final ReconcileIqProperties properties;

    public InvestigationService(
            ReconciliationResultRepository resultRepository,
            InvestigationReportRepository reportRepository,
            ReconciliationResultTransactionRepository resultTransactionRepository,
            AiInvestigationClient aiClient,
            InvestigationPromptBuilder promptBuilder,
            ObservedFactsBuilder factsBuilder,
            InvestigationOutputValidator validator,
            InvestigationFallbackFactory fallbackFactory,
            ReconcileIqProperties properties
    ) {
        this.resultRepository = resultRepository;
        this.reportRepository = reportRepository;
        this.resultTransactionRepository = resultTransactionRepository;
        this.aiClient = aiClient;
        this.promptBuilder = promptBuilder;
        this.factsBuilder = factsBuilder;
        this.validator = validator;
        this.fallbackFactory = fallbackFactory;
        this.properties = properties;
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

        List<PayPalTransaction> transactions = resultTransactionRepository
                .findAllByReconciliationResultId(result.getId())
                .stream()
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

        List<String> facts = factsBuilder.build(result, evidence);
        InvestigationPromptBuilder.Prompt prompt = promptBuilder.build(result, evidence);

        long started = System.currentTimeMillis();
        ValidatedInvestigation investigation = runModel(prompt, result, evidence, facts);
        long latency = System.currentTimeMillis() - started;

        ReconcileIqProperties.Ai ai = properties.getAi();
        return reportRepository.save(new InvestigationReport(
                result.getId(),
                facts,
                investigation,
                ai.getModel(),
                ai.getPromptVersion() == null
                        ? ReconcileIqProperties.Ai.PROMPT_VERSION
                        : ai.getPromptVersion(),
                latency
        ));
    }

    public ValidatedInvestigation runModel(
            InvestigationPromptBuilder.Prompt prompt,
            ReconciliationResult result,
            List<InvestigationEvidence> evidence,
            List<String> facts
    ) {
        String raw = null;
        try {
            raw = aiClient.complete(prompt.system(), prompt.user());
            InvestigationOutputValidator.ParseAttempt first = validator.parseAndValidate(raw, result, evidence);
            if (first.ok()) {
                return withStatus(first.value(), ValidationStatus.PASSED, raw);
            }
            log.debug("Investigation output rejected: {}", first.violations());
            String retryRaw = aiClient.complete(
                    prompt.system(),
                    prompt.user() + validator.correctiveSuffix(first.violations())
            );
            InvestigationOutputValidator.ParseAttempt second =
                    validator.parseAndValidate(retryRaw, result, evidence);
            if (second.ok()) {
                return withStatus(second.value(), ValidationStatus.PASSED_AFTER_RETRY, retryRaw);
            }
            log.debug("Investigation retry rejected: {}", second.violations());
            return fallbackFactory.fallback(result, evidence, facts, retryRaw);
        } catch (RuntimeException ex) {
            log.warn("AI investigation failed; using fallback: {}", ex.getMessage());
            return fallbackFactory.fallback(result, evidence, facts, raw);
        }
    }

    private static ValidatedInvestigation withStatus(
            ValidatedInvestigation value,
            ValidationStatus status,
            String raw
    ) {
        return new ValidatedInvestigation(
                value.summary(),
                value.possibleExplanations(),
                value.recommendedChecks(),
                true,
                status,
                raw,
                List.of()
        );
    }
}
