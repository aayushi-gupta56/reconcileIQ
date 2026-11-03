package com.reconcileiq.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository
        extends JpaRepository<ReconciliationRun, Long> {
}