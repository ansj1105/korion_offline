CREATE TABLE IF NOT EXISTS issued_offline_proofs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL,
    device_id VARCHAR(120) NOT NULL,
    collateral_id UUID NOT NULL REFERENCES collateral_locks(id),
    asset_code VARCHAR(32) NOT NULL,
    usable_amount NUMERIC(36, 8) NOT NULL,
    proof_nonce VARCHAR(128) NOT NULL,
    issuer_key_id VARCHAR(64) NOT NULL,
    issuer_public_key TEXT NOT NULL,
    issuer_signature TEXT NOT NULL,
    issued_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    consumed_by_proof_id UUID NULL REFERENCES offline_payment_proofs(id),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_issued_offline_proofs_user_device_asset
    ON issued_offline_proofs (user_id, device_id, asset_code, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_issued_offline_proofs_status
    ON issued_offline_proofs (status);
