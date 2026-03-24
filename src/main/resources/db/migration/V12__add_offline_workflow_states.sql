CREATE TABLE IF NOT EXISTS offline_workflow_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_type VARCHAR(64) NOT NULL,
    workflow_id VARCHAR(128) NOT NULL,
    workflow_stage VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    source_event_id UUID NOT NULL,
    batch_id UUID NULL,
    settlement_id VARCHAR(128) NULL,
    operation_id UUID NULL,
    proof_id VARCHAR(128) NULL,
    reference_id VARCHAR(255) NULL,
    asset_code VARCHAR(32) NULL,
    reason_code VARCHAR(128) NULL,
    error_message TEXT NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workflow_type, workflow_id)
);

CREATE INDEX IF NOT EXISTS idx_offline_workflow_states_stage
    ON offline_workflow_states (workflow_stage, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_offline_workflow_states_batch_id
    ON offline_workflow_states (batch_id);

CREATE INDEX IF NOT EXISTS idx_offline_workflow_states_operation_id
    ON offline_workflow_states (operation_id);
