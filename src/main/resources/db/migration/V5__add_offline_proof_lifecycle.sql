ALTER TABLE offline_payment_proofs
    ADD COLUMN IF NOT EXISTS channel_type VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS issued_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS settled_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_offline_payment_proofs_status
    ON offline_payment_proofs (status);

CREATE INDEX IF NOT EXISTS idx_offline_payment_proofs_channel_type
    ON offline_payment_proofs (channel_type);

CREATE INDEX IF NOT EXISTS idx_offline_payment_proofs_created_at
    ON offline_payment_proofs (created_at DESC);
