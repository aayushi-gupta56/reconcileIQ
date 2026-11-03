package com.reconcileiq.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation/results")
@CrossOrigin(origins = "http://localhost:5173")
public class InvestigationController {

    private final InvestigationService investigationService;
    private final InvestigationReportRepository reportRepository;

    public InvestigationController(
            InvestigationService investigationService,
            InvestigationReportRepository reportRepository
    ) {
        this.investigationService = investigationService;
        this.reportRepository = reportRepository;
    }

    @PostMapping("/{resultId}/investigate")
    public ResponseEntity<InvestigationReport> investigate(
            @PathVariable Long resultId
    ) {
        return ResponseEntity.ok(
                investigationService.investigate(resultId)
        );
    }

    @GetMapping("/{resultId}/investigations")
    public ResponseEntity<List<InvestigationReport>> getInvestigations(
            @PathVariable Long resultId
    ) {
        return ResponseEntity.ok(
                reportRepository
                        .findByReconciliationResultIdOrderByCreatedAtDesc(resultId)
        );
    }
}