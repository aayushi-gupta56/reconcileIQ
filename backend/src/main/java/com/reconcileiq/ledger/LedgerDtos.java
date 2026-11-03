package com.reconcileiq.ledger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record LedgerEntryResponse(
            Long id,
            String merchantOrderId,
            String paymentReference,
            BigDecimal amount,
            String currency,
            String status,
            LocalDateTime transactionDate,
            String description,
            LocalDateTime importedAt
    ) {

        public static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(
                    entry.getId(),
                    entry.getMerchantOrderId(),
                    entry.getPaymentReference(),
                    entry.getAmount(),
                    entry.getCurrency(),
                    entry.getStatus(),
                    entry.getTransactionDate(),
                    entry.getDescription(),
                    entry.getImportedAt()
            );
        }
    }

    public record ImportResponse(
            int importedCount,
            int rejectedCount,
            List<String> errors,
            String message
    ) {
    }
}