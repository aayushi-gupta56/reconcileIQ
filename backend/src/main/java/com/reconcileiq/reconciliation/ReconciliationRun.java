package com.reconcileiq.reconciliation;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int totalOrders;
    private int matchedCount;
    private int discrepancyCount;
    private int unmatchedPaymentsCount;

    private LocalDateTime startedAt;

    @PrePersist
    public void prePersist() {
        startedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(int matchedCount) {
        this.matchedCount = matchedCount;
    }

    public int getDiscrepancyCount() {
        return discrepancyCount;
    }

    public void setDiscrepancyCount(int discrepancyCount) {
        this.discrepancyCount = discrepancyCount;
    }

    public int getUnmatchedPaymentsCount() {
        return unmatchedPaymentsCount;
    }

    public void setUnmatchedPaymentsCount(int unmatchedPaymentsCount) {
        this.unmatchedPaymentsCount = unmatchedPaymentsCount;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }
}