package com.reconcileiq.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestigationReviewService {

    private final InvestigationReportRepository reportRepository;

    public InvestigationReviewService(InvestigationReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Transactional
    public InvestigationReport approve(Long id, ReviewRequest request) {
        InvestigationReport report = require(id);
        report.approve(request.reviewer(), request.note());
        return reportRepository.save(report);
    }

    @Transactional
    public InvestigationReport dismiss(Long id, ReviewRequest request) {
        InvestigationReport report = require(id);
        report.dismiss(request.reviewer(), request.note());
        return reportRepository.save(report);
    }

    @Transactional
    public InvestigationReport needsInfo(Long id, ReviewRequest request) {
        InvestigationReport report = require(id);
        report.needsInfo(request.reviewer(), request.note());
        return reportRepository.save(report);
    }

    private InvestigationReport require(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Investigation not found: " + id));
    }
}
