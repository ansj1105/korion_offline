CREATE TABLE IF NOT EXISTS reconciliation_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id UUID NULL REFERENCES settlement_requests(id),
    batch_id UUID NOT NULL REFERENCES settlement_batches(id),
    proof_id UUID NULL REFERENCES offline_payment_proofs(id),
    voucher_id VARCHAR(64),
    case_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reason_code VARCHAR(64) NOT NULL,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_reconciliation_cases_status
    ON reconciliation_cases (status);

CREATE INDEX IF NOT EXISTS idx_reconciliation_cases_batch_id
    ON reconciliation_cases (batch_id);

CREATE INDEX IF NOT EXISTS idx_reconciliation_cases_voucher_id
    ON reconciliation_cases (voucher_id);

CREATE INDEX IF NOT EXISTS idx_reconciliation_cases_reason_code
    ON reconciliation_cases (reason_code);
