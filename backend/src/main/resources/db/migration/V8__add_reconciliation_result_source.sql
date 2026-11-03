ALTER TABLE reconciliation_results
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'DEMO';

ALTER TABLE reconciliation_results
    ADD CONSTRAINT chk_reconciliation_result_source
        CHECK (source IN ('DEMO', 'SANDBOX'));

CREATE INDEX idx_reconciliation_results_source
    ON reconciliation_results(source);
