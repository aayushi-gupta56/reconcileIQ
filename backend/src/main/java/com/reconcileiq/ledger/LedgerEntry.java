package com.reconcileiq.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "merchant_order_id",
        nullable = false,
        length = 120
    )
    private String merchantOrderId;

    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Column(length = 500)
    private String description;

    @Column(
        name = "imported_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    private LocalDateTime importedAt;

    protected LedgerEntry() {
        // Required by JPA
    }

    public LedgerEntry(
            String merchantOrderId,
            String paymentReference,
            BigDecimal amount,
            String currency,
            String status,
            LocalDateTime transactionDate,
            String description) {

        this.merchantOrderId = merchantOrderId;
        this.paymentReference = paymentReference;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.transactionDate = transactionDate;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getMerchantOrderId() {
        return merchantOrderId;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }
}