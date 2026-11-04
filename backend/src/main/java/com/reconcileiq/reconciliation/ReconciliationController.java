package com.reconcileiq.reconciliation;

import com.reconcileiq.ai.InvestigationReport;
import com.reconcileiq.ai.InvestigationReportRepository;
import com.reconcileiq.notification.ZapierNotifier;
import com.reconcileiq.paypal.PayPalIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reconciliation")
@CrossOrigin(origins = "http://localhost:5173")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final ReconciliationService reconciliationService;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationResultRepository resultRepository;
    private final PayPalIngestionService payPalIngestionService;
    private final InvestigationReportRepository reportRepository;
    private final ZapierNotifier zapierNotifier;

    public ReconciliationController(
            ReconciliationService reconciliationService,
            ReconciliationRunRepository runRepository,
            ReconciliationResultRepository resultRepository,
            PayPalIngestionService payPalIngestionService,
            InvestigationReportRepository reportRepository,
            ZapierNotifier zapierNotifier
    ) {
        this.reconciliationService = reconciliationService;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.payPalIngestionService = payPalIngestionService;
        this.reportRepository = reportRepository;
        this.zapierNotifier = zapierNotifier;
    }

    @PostMapping("/run")
    public ReconciliationRun run() {
        payPalIngestionService.refreshIfLive();
        ReconciliationRun run = reconciliationService.runReconciliation();
        // The run is already committed. A notification problem must never fail it.
        try {
            zapierNotifier.notifyRunCompleted(run);
        } catch (Exception ex) {
            log.warn("Run #{} completed but notification failed: {}", run.getId(), ex.toString());
        }
        return run;
    }

    @GetMapping("/runs")
    public List<ReconciliationRun> getRuns() {
        return runRepository.findAll();
    }

    @GetMapping("/runs/{runId}/results")
    public List<ReconciliationResultView> getResults(
            @PathVariable Long runId
    ) {
        List<ReconciliationResult> results =
                resultRepository.findByReconciliationRunIdOrderByIdAsc(runId);
        if (results.isEmpty()) {
            return List.of();
        }
        Map<Long, InvestigationReport> latest = reportRepository
                .findByReconciliationResultIdIn(results.stream().map(ReconciliationResult::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        InvestigationReport::getReconciliationResultId,
                        report -> report,
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b
                ));
        return results.stream()
                .map(result -> ReconciliationResultView.from(result, latest.get(result.getId())))
                .toList();
    }
}