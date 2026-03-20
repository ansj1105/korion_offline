CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(120) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    public_key TEXT NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS device_risk_profiles (
    device_id VARCHAR(120) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    max_offline_amount NUMERIC(36, 8) NOT NULL,
    policy_version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS collateral_locks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL,
    device_id VARCHAR(120) NOT NULL,
    asset_code VARCHAR(20) NOT NULL,
    locked_amount NUMERIC(36, 8) NOT NULL,
    remaining_amount NUMERIC(36, 8) NOT NULL,
    initial_state_root VARCHAR(160) NOT NULL,
    policy_version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL,
    external_lock_id VARCHAR(120),
    expires_at TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_collateral_locks_user_id ON collateral_locks (user_id);
CREATE INDEX IF NOT EXISTS idx_collateral_locks_device_id ON collateral_locks (device_id);

CREATE TABLE IF NOT EXISTS offline_payment_proofs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collateral_id UUID NOT NULL REFERENCES collateral_locks(id),
    sender_device_id VARCHAR(120) NOT NULL,
    receiver_device_id VARCHAR(120) NOT NULL,
    hash_chain_head VARCHAR(160) NOT NULL,
    previous_hash VARCHAR(160),
    signature TEXT NOT NULL,
    amount NUMERIC(36, 8) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS settlement_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_device_id VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    proofs_count INTEGER NOT NULL DEFAULT 0,
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS settlement_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL REFERENCES settlement_batches(id),
    collateral_id UUID NOT NULL REFERENCES collateral_locks(id),
    proof_id UUID NOT NULL REFERENCES offline_payment_proofs(id),
    status VARCHAR(30) NOT NULL,
    settlement_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    conflict_detected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_settlement_requests_status ON settlement_requests (status);
CREATE INDEX IF NOT EXISTS idx_settlement_requests_batch_id ON settlement_requests (batch_id);

CREATE TABLE IF NOT EXISTS settlement_conflicts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id UUID NOT NULL REFERENCES settlement_requests(id),
    conflict_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

