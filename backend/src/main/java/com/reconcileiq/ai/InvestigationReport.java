package com.reconcileiq.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "investigation_reports")
public class InvestigationReport {

    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String APPROVED = "APPROVED";
    public static final String DISMISSED = "DISMISSED";
    public static final String NEEDS_INFO = "NEEDS_INFO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reconciliation_result_id", nullable = false)
    private Long reconciliationResultId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "possible_explanations", nullable = false, columnDefinition = "jsonb")
    private List<String> possibleExplanations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "observed_facts", nullable = false, columnDefinition = "jsonb")
    private List<String> observedFacts = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_checks", nullable = false, columnDefinition = "jsonb")
    private List<String> recommendedChecks = new ArrayList<>();

    @Column(name = "requires_human_review", nullable = false)
    private boolean requiresHumanReview = true;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "validation_status", nullable = false)
    private String validationStatus = ValidationStatus.FALLBACK.name();

    @Column(nullable = false)
    private String status = PENDING_REVIEW;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected InvestigationReport() {
    }

    public InvestigationReport(
            Long reconciliationResultId,
            List<String> observedFacts,
            ValidatedInvestigation investigation,
            String modelName,
            String promptVersion,
            long latencyMs
    ) {
        this.reconciliationResultId = reconciliationResultId;
        this.summary = investigation.summary();
        this.possibleExplanations = new ArrayList<>(investigation.possibleExplanations());
        this.observedFacts = new ArrayList<>(observedFacts);
        this.recommendedChecks = new ArrayList<>(investigation.recommendedChecks());
        this.requiresHumanReview = true;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.rawResponse = investigation.rawResponse();
        this.latencyMs = latencyMs;
        this.validationStatus = investigation.validationStatus().name();
        this.status = PENDING_REVIEW;
        this.createdAt = LocalDateTime.now();
    }

    public void approve(String reviewer, String note) {
        assertPending();
        this.status = APPROVED;
        recordReview(reviewer, note);
    }

    public void dismiss(String reviewer, String note) {
        assertPending();
        this.status = DISMISSED;
        recordReview(reviewer, note);
    }

    public void needsInfo(String reviewer, String note) {
        assertPending();
        this.status = NEEDS_INFO;
        recordReview(reviewer, note);
    }

    private void assertPending() {
        if (!PENDING_REVIEW.equals(status) && !NEEDS_INFO.equals(status)) {
            throw new IllegalArgumentException(
                    "Cannot change review status of an investigation that is " + status);
        }
    }

    private void recordReview(String reviewer, String note) {
        if (reviewer == null || reviewer.isBlank()) {
            throw new IllegalArgumentException("reviewer is required");
        }
        this.reviewedBy = reviewer.trim();
        this.reviewNote = note;
        this.reviewedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getReconciliationResultId() {
        return reconciliationResultId;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getPossibleExplanations() {
        return possibleExplanations;
    }

    public List<String> getObservedFacts() {
        return observedFacts;
    }

    public List<String> getRecommendedChecks() {
        return recommendedChecks;
    }

    public boolean isRequiresHumanReview() {
        return requiresHumanReview;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public String getStatus() {
        return status;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
