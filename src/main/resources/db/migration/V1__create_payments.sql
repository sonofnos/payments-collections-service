CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    idempotency_key     VARCHAR(128) NOT NULL,
    debtor_account_id   VARCHAR(64) NOT NULL,
    creditor_account_id VARCHAR(64) NOT NULL,
    amount              NUMERIC(19,4) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    narrative           VARCHAR(500),
    status              VARCHAR(16) NOT NULL,
    failure_reason      VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_payments_idempotency_key ON payments (idempotency_key);
CREATE INDEX idx_payments_status ON payments (status);
