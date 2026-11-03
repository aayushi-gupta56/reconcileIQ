package com.reconcileiq.reconciliation;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation")
@CrossOrigin(origins = "http://localhost:5173")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationResultRepository resultRepository;

    public ReconciliationController(
            ReconciliationService reconciliationService,
            ReconciliationRunRepository runRepository,
            ReconciliationResultRepository resultRepository
    ) {
        this.reconciliationService = reconciliationService;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
    }

    @PostMapping("/run")
    public ReconciliationRun run() {
        return reconciliationService.runReconciliation();
    }

    @GetMapping("/runs")
    public List<ReconciliationRun> getRuns() {
        return runRepository.findAll();
    }

    @GetMapping("/runs/{runId}/results")
    public List<ReconciliationResult> getResults(
            @PathVariable Long runId
    ) {
        return resultRepository
                .findByReconciliationRunIdOrderByIdAsc(runId);
    }
}