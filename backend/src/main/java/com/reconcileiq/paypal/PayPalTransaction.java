package com.reconcileiq.paypal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paypal_transactions")
public class PayPalTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paypal_order_id", nullable = false)
    private String paypalOrderId;

    @Column(name = "capture_id", nullable = false, unique = true)
    private String captureId;

    @Column(name = "merchant_order_id")
    private String merchantOrderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String status;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    protected PayPalTransaction() {
    }

    public PayPalTransaction(
            String paypalOrderId,
            String captureId,
            String merchantOrderId,
            BigDecimal amount,
            String currency,
            String status,
            LocalDateTime capturedAt) {
        this.paypalOrderId = paypalOrderId;
        this.captureId = captureId;
        this.merchantOrderId = merchantOrderId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.capturedAt = capturedAt;
    }

    public Long getId() {
        return id;
    }

    public String getPaypalOrderId() {
        return paypalOrderId;
    }

    public String getCaptureId() {
        return captureId;
    }

    public String getMerchantOrderId() {
        return merchantOrderId;
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

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }
}