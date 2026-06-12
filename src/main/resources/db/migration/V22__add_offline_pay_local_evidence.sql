CREATE TABLE IF NOT EXISTS offline_pay_local_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proof_id UUID NULL REFERENCES offline_payment_proofs(id),
    voucher_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(160),
    direction VARCHAR(16) NOT NULL,
    uploader_type VARCHAR(16) NOT NULL,
    uploader_device_id VARCHAR(120) NOT NULL,
    sender_device_id VARCHAR(120) NOT NULL,
    receiver_device_id VARCHAR(120) NOT NULL,
    amount NUMERIC(36, 8) NOT NULL,
    counter BIGINT,
    previous_hash VARCHAR(160),
    hash_chain_head VARCHAR(160),
    nonce VARCHAR(160) NOT NULL,
    signature TEXT,
    canonical_payload TEXT,
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    verification_detail TEXT,
    matched_proof_id UUID NULL REFERENCES offline_payment_proofs(id),
    matched_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_offline_pay_local_evidence_identity
    ON offline_pay_local_evidence (voucher_id, direction, uploader_device_id, nonce);

CREATE INDEX IF NOT EXISTS idx_offline_pay_local_evidence_proof_id
    ON offline_pay_local_evidence (proof_id);

CREATE INDEX IF NOT EXISTS idx_offline_pay_local_evidence_voucher_direction
    ON offline_pay_local_evidence (voucher_id, direction);

CREATE INDEX IF NOT EXISTS idx_offline_pay_local_evidence_unmatched
    ON offline_pay_local_evidence (voucher_id, direction, created_at)
    WHERE matched_at IS NULL;
