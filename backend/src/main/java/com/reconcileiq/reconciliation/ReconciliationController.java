package com.reconcileiq.reconciliation;

import com.reconcileiq.ai.InvestigationReport;
import com.reconcileiq.ai.InvestigationReportRepository;
import com.reconcileiq.paypal.PayPalIngestionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reconciliation")
@CrossOrigin(origins = "http://localhost:5173")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationResultRepository resultRepository;
    private final PayPalIngestionService payPalIngestionService;
    private final InvestigationReportRepository reportRepository;

    public ReconciliationController(
            ReconciliationService reconciliationService,
            ReconciliationRunRepository runRepository,
            ReconciliationResultRepository resultRepository,
            PayPalIngestionService payPalIngestionService,
            InvestigationReportRepository reportRepository
    ) {
        this.reconciliationService = reconciliationService;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.payPalIngestionService = payPalIngestionService;
        this.reportRepository = reportRepository;
    }

    @PostMapping("/run")
    public ReconciliationRun run() {
        payPalIngestionService.refreshIfLive();
        return reconciliationService.runReconciliation();
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