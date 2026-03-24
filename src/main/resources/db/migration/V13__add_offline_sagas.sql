CREATE TABLE IF NOT EXISTS offline_sagas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_type VARCHAR(64) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    current_step VARCHAR(64) NOT NULL,
    last_reason_code VARCHAR(128) NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (saga_type, reference_id)
);

CREATE INDEX IF NOT EXISTS idx_offline_sagas_status
    ON offline_sagas (status, updated_at DESC);
