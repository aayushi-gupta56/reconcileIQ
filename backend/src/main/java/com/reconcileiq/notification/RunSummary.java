package com.reconcileiq.notification;

import java.time.LocalDateTime;

/**
 * Everything the notification needs to know about a finished reconciliation run.
 * Counts use the same definitions as the dashboard summary cards.
 */
public record RunSummary(
        long runId,
        LocalDateTime startedAt,
        int totalOrders,
        int matched,
        int discrepancies,
        int unexpectedPayments,
        String reportUrl
) {
    public boolean needsAttention() {
        return discrepancies + unexpectedPayments > 0;
    }
}
