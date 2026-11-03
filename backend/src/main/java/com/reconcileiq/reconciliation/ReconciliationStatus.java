package com.reconcileiq.reconciliation;

public enum ReconciliationStatus {
    MATCHED,
    MISSING_PAYMENT,
    AMOUNT_MISMATCH,
    CURRENCY_MISMATCH,
    DUPLICATE_PAYMENT,
    REFERENCE_MISMATCH,
    UNEXPECTED_PAYMENT
}