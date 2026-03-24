CREATE TABLE IF NOT EXISTS settlement_outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    batch_id UUID,
    uploader_type VARCHAR(32),
    uploader_device_id VARCHAR(128),
    operation_id UUID,
    operation_type VARCHAR(64),
    asset_code VARCHAR(32),
    reference_id VARCHAR(128),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    attempts INTEGER NOT NULL DEFAULT 0,
    lock_owner VARCHAR(128),
    locked_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ,
    reason_code VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_settlement_outbox_event_poll
    ON settlement_outbox_events (event_type, status, created_at);

CREATE INDEX IF NOT EXISTS idx_settlement_outbox_event_batch
    ON settlement_outbox_events (batch_id, event_type, status);
