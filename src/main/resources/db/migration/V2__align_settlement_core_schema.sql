ALTER TABLE offline_payment_proofs
    ADD COLUMN IF NOT EXISTS batch_id UUID NULL REFERENCES settlement_batches(id),
    ADD COLUMN IF NOT EXISTS voucher_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS key_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS policy_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS counter BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS nonce VARCHAR(128),
    ADD COLUMN IF NOT EXISTS timestamp_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS expires_at_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS canonical_payload TEXT,
    ADD COLUMN IF NOT EXISTS uploader_type VARCHAR(16) NOT NULL DEFAULT 'SENDER',
    ADD COLUMN IF NOT EXISTS raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE offline_payment_proofs
SET raw_payload = payload
WHERE raw_payload = '{}'::jsonb;

CREATE UNIQUE INDEX IF NOT EXISTS uk_offline_payment_proofs_voucher_id
    ON offline_payment_proofs (voucher_id)
    WHERE voucher_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_offline_payment_proofs_sender_nonce
    ON offline_payment_proofs (sender_device_id, nonce)
    WHERE nonce IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_offline_payment_proofs_collateral_counter
    ON offline_payment_proofs (collateral_id, counter);

CREATE TABLE IF NOT EXISTS settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id UUID NOT NULL UNIQUE REFERENCES settlement_requests(id),
    batch_id UUID NOT NULL REFERENCES settlement_batches(id),
    voucher_id VARCHAR(64) NOT NULL UNIQUE,
    collateral_id UUID NOT NULL REFERENCES collateral_locks(id),
    sender_device_id VARCHAR(120) NOT NULL,
    receiver_device_id VARCHAR(120),
    status VARCHAR(30) NOT NULL,
    reason_code VARCHAR(64),
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    settled_amount NUMERIC(36, 8),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_settlements_batch_id ON settlements (batch_id);
CREATE INDEX IF NOT EXISTS idx_settlements_collateral_id ON settlements (collateral_id);
CREATE INDEX IF NOT EXISTS idx_settlements_status ON settlements (status);

ALTER TABLE settlement_conflicts
    ADD COLUMN IF NOT EXISTS voucher_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS collateral_id UUID NULL REFERENCES collateral_locks(id),
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_settlement_conflicts_status ON settlement_conflicts (status);
CREATE INDEX IF NOT EXISTS idx_settlement_conflicts_collateral_id ON settlement_conflicts (collateral_id);
CREATE INDEX IF NOT EXISTS idx_settlement_conflicts_device_id ON settlement_conflicts (device_id);
CREATE INDEX IF NOT EXISTS idx_settlement_conflicts_voucher_id ON settlement_conflicts (voucher_id);
