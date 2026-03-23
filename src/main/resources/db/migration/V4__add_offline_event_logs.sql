CREATE TABLE IF NOT EXISTS offline_event_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL,
    device_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    event_status VARCHAR(32) NOT NULL,
    asset_code VARCHAR(20),
    network_code VARCHAR(32),
    amount NUMERIC(36, 8),
    request_id VARCHAR(160),
    settlement_id VARCHAR(160),
    counterparty_device_id VARCHAR(120),
    counterparty_actor VARCHAR(120),
    reason_code VARCHAR(80),
    message TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_offline_event_logs_status
    ON offline_event_logs (event_status);

CREATE INDEX IF NOT EXISTS idx_offline_event_logs_type_status
    ON offline_event_logs (event_type, event_status);

CREATE INDEX IF NOT EXISTS idx_offline_event_logs_user_id
    ON offline_event_logs (user_id);

CREATE INDEX IF NOT EXISTS idx_offline_event_logs_device_id
    ON offline_event_logs (device_id);

CREATE INDEX IF NOT EXISTS idx_offline_event_logs_created_at
    ON offline_event_logs (created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_offline_event_logs_request_status
    ON offline_event_logs (request_id, event_type, event_status)
    WHERE request_id IS NOT NULL;
