package com.reconcileiq.eval;

import java.util.List;

public record EvalCase(
        String id,
        String status,
        String merchantOrderId,
        String paypalOrderId,
        String captureId,
        String expectedAmount,
        String actualAmount,
        String expectedCurrency,
        String actualCurrency,
        Integer matchedTransactionCount,
        String explanation,
        List<Evidence> evidence,
        String modelOutput
) {
    public record Evidence(
            String captureId,
            String paypalOrderId,
            String merchantOrderId,
            String amount,
            String currency,
            String status,
            String capturedAt
    ) {}
}
