package com.reconcileiq.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvestigationReportRepository
        extends JpaRepository<InvestigationReport, Long> {

    List<InvestigationReport> findByReconciliationResultIdOrderByCreatedAtDesc(
            Long reconciliationResultId
    );

    List<InvestigationReport> findByReconciliationResultIdIn(List<Long> reconciliationResultIds);
}