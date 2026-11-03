CREATE TABLE reconciliation_runs (
    id BIGSERIAL PRIMARY KEY,
    total_orders INTEGER NOT NULL DEFAULT 0,
    matched_count INTEGER NOT NULL DEFAULT 0,
    discrepancy_count INTEGER NOT NULL DEFAULT 0,
    unmatched_payments_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reconciliation_results (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_run_id BIGINT NOT NULL,
    merchant_order_id VARCHAR(120),
    paypal_order_id VARCHAR(120),
    capture_id VARCHAR(120),

    expected_amount NUMERIC(19, 2),
    actual_amount NUMERIC(19, 2),

    expected_currency VARCHAR(3),
    actual_currency VARCHAR(3),

    status VARCHAR(50) NOT NULL,
    matched_transaction_count INTEGER NOT NULL DEFAULT 0,
    explanation TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reconciliation_run
        FOREIGN KEY (reconciliation_run_id)
        REFERENCES reconciliation_runs(id)
);

CREATE INDEX idx_reconciliation_results_run
    ON reconciliation_results(reconciliation_run_id);

CREATE INDEX idx_reconciliation_results_status
    ON reconciliation_results(status);