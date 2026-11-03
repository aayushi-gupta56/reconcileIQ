CREATE TABLE merchant_orders (
    id BIGSERIAL PRIMARY KEY,
    merchant_order_id VARCHAR(120) NOT NULL UNIQUE,
    expected_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    paypal_order_id VARCHAR(120) UNIQUE,
    approval_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_merchant_order_amount
        CHECK (expected_amount > 0),

    CONSTRAINT chk_merchant_order_currency
        CHECK (char_length(trim(currency)) = 3)
);

CREATE INDEX idx_merchant_orders_status
    ON merchant_orders(status);