package com.reconcileiq.ai;

import java.math.BigDecimal;
import java.util.List;

public record InvestigationResponse(
        String likelyCause,
        BigDecimal confidence,
        List<String> evidence,
        String recommendedAction,
        boolean requiresHumanReview
) {}