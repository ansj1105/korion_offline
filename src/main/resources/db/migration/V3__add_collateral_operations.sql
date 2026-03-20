CREATE TABLE IF NOT EXISTS collateral_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collateral_id UUID NULL REFERENCES collateral_locks(id),
    user_id BIGINT NOT NULL,
    device_id VARCHAR(120) NOT NULL,
    asset_code VARCHAR(20) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    amount NUMERIC(36, 8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reference_id VARCHAR(160) NOT NULL,
    error_message TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_collateral_operations_reference_id
    ON collateral_operations (reference_id);

CREATE INDEX IF NOT EXISTS idx_collateral_operations_status
    ON collateral_operations (status);

CREATE INDEX IF NOT EXISTS idx_collateral_operations_type_status
    ON collateral_operations (operation_type, status);

CREATE INDEX IF NOT EXISTS idx_collateral_operations_user_id
    ON collateral_operations (user_id);

CREATE INDEX IF NOT EXISTS idx_collateral_operations_created_at
    ON collateral_operations (created_at DESC);
