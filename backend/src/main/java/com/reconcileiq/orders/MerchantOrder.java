package com.reconcileiq.orders;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "merchant_orders")
public class MerchantOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_order_id", nullable = false, unique = true)
    private String merchantOrderId;

    @Column(name = "expected_amount", nullable = false,
            precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String status;

    @Column(name = "paypal_order_id", unique = true)
    private String paypalOrderId;

    @Column(name = "approval_url", columnDefinition = "TEXT")
    private String approvalUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MerchantOrder() {
    }

    public MerchantOrder(
            String merchantOrderId,
            BigDecimal expectedAmount,
            String currency) {

        this.merchantOrderId = merchantOrderId;
        this.expectedAmount = expectedAmount;
        this.currency = currency;
        this.status = "CREATING";
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getMerchantOrderId() {
        return merchantOrderId;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public String getPaypalOrderId() {
        return paypalOrderId;
    }

    public String getApprovalUrl() {
        return approvalUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setPaypalOrderId(String paypalOrderId) {
        this.paypalOrderId = paypalOrderId;
    }

    public void setApprovalUrl(String approvalUrl) {
        this.approvalUrl = approvalUrl;
    }
}