package com.reconcileiq.ai;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvestigationEvidence(
        String captureId,
        String paypalOrderId,
        String merchantOrderId,
        BigDecimal amount,
        String currency,
        String status,
        LocalDateTime capturedAt
) {}