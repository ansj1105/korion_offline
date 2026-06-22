CREATE TABLE IF NOT EXISTS offline_pay_reconcile_commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    projection_version VARCHAR(160) NOT NULL,
    nonce VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    delivered_to_device_id VARCHAR(120),
    delivered_at TIMESTAMPTZ,
    applied_by_device_id VARCHAR(120),
    applied_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    error_message TEXT,
    dry_run_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    apply_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    local_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_offline_pay_reconcile_commands_nonce
    ON offline_pay_reconcile_commands (nonce);

CREATE INDEX IF NOT EXISTS idx_offline_pay_reconcile_commands_scope
    ON offline_pay_reconcile_commands (user_id, asset_code, status, expires_at DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_offline_pay_reconcile_commands_pending
    ON offline_pay_reconcile_commands (user_id, asset_code, expires_at DESC, created_at DESC)
    WHERE status IN ('PENDING', 'DELIVERED');
