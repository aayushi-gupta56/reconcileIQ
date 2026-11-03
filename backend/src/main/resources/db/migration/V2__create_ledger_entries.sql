CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,

    merchant_order_id VARCHAR(120) NOT NULL,

    payment_reference VARCHAR(120),

    amount NUMERIC(19, 4) NOT NULL,

    currency VARCHAR(3) NOT NULL,

    status VARCHAR(40) NOT NULL,

    transaction_date TIMESTAMP,

    description VARCHAR(500),

    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ledger_amount_nonnegative
        CHECK (amount >= 0),

    CONSTRAINT chk_ledger_currency_length
        CHECK (char_length(trim(currency)) = 3)
);

CREATE INDEX idx_ledger_merchant_order_id
    ON ledger_entries (merchant_order_id);

CREATE INDEX idx_ledger_payment_reference
    ON ledger_entries (payment_reference);

CREATE INDEX idx_ledger_transaction_date
    ON ledger_entries (transaction_date);