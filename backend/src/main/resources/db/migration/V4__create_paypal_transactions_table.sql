CREATE TABLE paypal_transactions (
    id BIGSERIAL PRIMARY KEY,
    paypal_order_id VARCHAR(120) NOT NULL,
    capture_id VARCHAR(120) NOT NULL UNIQUE,
    merchant_order_id VARCHAR(120),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    captured_at TIMESTAMP,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_paypal_transactions_order_id
    ON paypal_transactions(paypal_order_id);

CREATE INDEX idx_paypal_transactions_merchant_order_id
    ON paypal_transactions(merchant_order_id);