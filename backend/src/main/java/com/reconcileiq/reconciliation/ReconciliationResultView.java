package com.reconcileiq.reconciliation;

import com.reconcileiq.ai.InvestigationReport;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReconciliationResultView(
        Long id,
        Long reconciliationRunId,
        String merchantOrderId,
        String paypalOrderId,
        String captureId,
        BigDecimal expectedAmount,
        BigDecimal actualAmount,
        String expectedCurrency,
        String actualCurrency,
        ReconciliationStatus status,
        int matchedTransactionCount,
        String explanation,
        String source,
        LocalDateTime createdAt,
        String reviewStatus,
        Long investigationId,
        String validationStatus
) {
    public static ReconciliationResultView from(ReconciliationResult result, InvestigationReport report) {
        return new ReconciliationResultView(
                result.getId(),
                result.getReconciliationRunId(),
                result.getMerchantOrderId(),
                result.getPaypalOrderId(),
                result.getCaptureId(),
                result.getExpectedAmount(),
                result.getActualAmount(),
                result.getExpectedCurrency(),
                result.getActualCurrency(),
                result.getStatus(),
                result.getMatchedTransactionCount(),
                result.getExplanation(),
                result.getSource(),
                result.getCreatedAt(),
                report == null ? null : report.getStatus(),
                report == null ? null : report.getId(),
                report == null ? null : report.getValidationStatus()
        );
    }
}
