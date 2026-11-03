CREATE TABLE investigation_reports (
    id BIGSERIAL PRIMARY KEY,

    reconciliation_result_id BIGINT NOT NULL,

    likely_cause TEXT NOT NULL,
    confidence NUMERIC(4,3) NOT NULL,
    evidence TEXT NOT NULL,
    recommended_action TEXT NOT NULL,

    requires_human_review BOOLEAN NOT NULL DEFAULT TRUE,

    model_name VARCHAR(120) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REVIEW',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_investigation_result
        FOREIGN KEY (reconciliation_result_id)
        REFERENCES reconciliation_results(id),

    CONSTRAINT chk_investigation_confidence
        CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE INDEX idx_investigation_result
    ON investigation_reports(reconciliation_result_id);