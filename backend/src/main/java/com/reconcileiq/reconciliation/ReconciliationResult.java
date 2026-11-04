package com.reconcileiq.reconciliation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_results")
public class ReconciliationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reconciliationRunId;

    private String merchantOrderId;
    private String paypalOrderId;
    private String captureId;

    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;

    private String expectedCurrency;
    private String actualCurrency;

    @Enumerated(EnumType.STRING)
    private ReconciliationStatus status;

    private int matchedTransactionCount;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false, length = 20)
    private String source = "DEMO";

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getReconciliationRunId() {
        return reconciliationRunId;
    }

    public void setReconciliationRunId(Long reconciliationRunId) {
        this.reconciliationRunId = reconciliationRunId;
    }

    public String getMerchantOrderId() {
        return merchantOrderId;
    }

    public void setMerchantOrderId(String merchantOrderId) {
        this.merchantOrderId = merchantOrderId;
    }

    public String getPaypalOrderId() {
        return paypalOrderId;
    }

    public void setPaypalOrderId(String paypalOrderId) {
        this.paypalOrderId = paypalOrderId;
    }

    public String getCaptureId() {
        return captureId;
    }

    public void setCaptureId(String captureId) {
        this.captureId = captureId;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }

    public String getExpectedCurrency() {
        return expectedCurrency;
    }

    public void setExpectedCurrency(String expectedCurrency) {
        this.expectedCurrency = expectedCurrency;
    }

    public String getActualCurrency() {
        return actualCurrency;
    }

    public void setActualCurrency(String actualCurrency) {
        this.actualCurrency = actualCurrency;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationStatus status) {
        this.status = status;
    }

    public int getMatchedTransactionCount() {
        return matchedTransactionCount;
    }

    public void setMatchedTransactionCount(int matchedTransactionCount) {
        this.matchedTransactionCount = matchedTransactionCount;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}