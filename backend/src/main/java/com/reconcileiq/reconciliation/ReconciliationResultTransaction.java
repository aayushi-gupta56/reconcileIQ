package com.reconcileiq.reconciliation;

import com.reconcileiq.paypal.PayPalTransaction;
import jakarta.persistence.*;

@Entity
@Table(
    name = "reconciliation_result_transactions",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {
            "reconciliation_result_id",
            "paypal_transaction_id"
        }
    )
)
public class ReconciliationResultTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reconciliation_result_id", nullable = false)
    private Long reconciliationResultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paypal_transaction_id", nullable = false)
    private PayPalTransaction paypalTransaction;

    protected ReconciliationResultTransaction() {}

    public ReconciliationResultTransaction(
            Long reconciliationResultId,
            PayPalTransaction paypalTransaction) {
        this.reconciliationResultId = reconciliationResultId;
        this.paypalTransaction = paypalTransaction;
    }

    public PayPalTransaction getPaypalTransaction() {
        return paypalTransaction;
    }
}