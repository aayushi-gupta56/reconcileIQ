package com.reconcileiq.ai;

import java.util.List;

public record ValidatedInvestigation(
        String summary,
        List<String> possibleExplanations,
        List<String> recommendedChecks,
        boolean requiresHumanReview,
        ValidationStatus validationStatus,
        String rawResponse,
        List<String> violations
) {
    public boolean passed() {
        return validationStatus == ValidationStatus.PASSED
                || validationStatus == ValidationStatus.PASSED_AFTER_RETRY;
    }
}
