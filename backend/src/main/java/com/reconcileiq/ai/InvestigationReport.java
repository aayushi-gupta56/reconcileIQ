package com.reconcileiq.ai;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "investigation_reports")
public class InvestigationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reconciliation_result_id", nullable = false)
    private Long reconciliationResultId;

    @Column(name = "likely_cause", nullable = false, columnDefinition = "TEXT")
    private String likelyCause;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "recommended_action", nullable = false, columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "requires_human_review", nullable = false)
    private boolean requiresHumanReview = true;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(nullable = false)
    private String status = "PENDING_REVIEW";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected InvestigationReport() {
    }

    public InvestigationReport(
            Long reconciliationResultId,
            InvestigationResponse response,
            String modelName
    ) {
        this.reconciliationResultId = reconciliationResultId;
        this.likelyCause = response.likelyCause();
        this.confidence = response.confidence();
        this.evidence = String.join("\n", response.evidence());
        this.recommendedAction = response.recommendedAction();
        this.requiresHumanReview = true;
        this.modelName = modelName;
        this.status = "PENDING_REVIEW";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getReconciliationResultId() {
        return reconciliationResultId;
    }

    public String getLikelyCause() {
        return likelyCause;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public boolean isRequiresHumanReview() {
        return requiresHumanReview;
    }

    public String getModelName() {
        return modelName;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}