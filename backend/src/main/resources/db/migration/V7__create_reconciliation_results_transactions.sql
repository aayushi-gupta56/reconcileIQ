CREATE TABLE reconciliation_result_transactions (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_result_id BIGINT NOT NULL,
    paypal_transaction_id BIGINT NOT NULL,

    CONSTRAINT fk_result_transaction_result
        FOREIGN KEY (reconciliation_result_id)
        REFERENCES reconciliation_results(id),

    CONSTRAINT fk_result_transaction_transaction
        FOREIGN KEY (paypal_transaction_id)
        REFERENCES paypal_transactions(id),

    CONSTRAINT uk_result_transaction
        UNIQUE (reconciliation_result_id, paypal_transaction_id)
);