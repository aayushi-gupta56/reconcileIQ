package com.reconcileiq.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationResultTransactionRepository
        extends JpaRepository<ReconciliationResultTransaction, Long> {

    List<ReconciliationResultTransaction>
        findAllByReconciliationResultId(Long resultId);
}