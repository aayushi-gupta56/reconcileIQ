ALTER TABLE investigation_reports
    ADD COLUMN summary TEXT,
    ADD COLUMN possible_explanations JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN observed_facts JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN recommended_checks JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN prompt_version VARCHAR(40),
    ADD COLUMN raw_response TEXT,
    ADD COLUMN latency_ms BIGINT,
    ADD COLUMN validation_status VARCHAR(40),
    ADD COLUMN reviewed_by VARCHAR(120),
    ADD COLUMN reviewed_at TIMESTAMP,
    ADD COLUMN review_note TEXT;

UPDATE investigation_reports
SET summary = likely_cause,
    observed_facts = to_jsonb(ARRAY[evidence]),
    recommended_checks = to_jsonb(ARRAY[recommended_action]),
    validation_status = 'FALLBACK',
    prompt_version = 'v1'
WHERE summary IS NULL;

ALTER TABLE investigation_reports
    ALTER COLUMN summary SET NOT NULL,
    ALTER COLUMN validation_status SET NOT NULL;

ALTER TABLE investigation_reports
    DROP CONSTRAINT IF EXISTS chk_investigation_confidence;

ALTER TABLE investigation_reports
    DROP COLUMN confidence,
    DROP COLUMN likely_cause,
    DROP COLUMN evidence,
    DROP COLUMN recommended_action;

ALTER TABLE investigation_reports
    ADD CONSTRAINT chk_investigation_validation_status
        CHECK (validation_status IN ('PASSED', 'PASSED_AFTER_RETRY', 'FALLBACK'));

ALTER TABLE investigation_reports
    ADD CONSTRAINT chk_investigation_review_status
        CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'DISMISSED', 'NEEDS_INFO'));
