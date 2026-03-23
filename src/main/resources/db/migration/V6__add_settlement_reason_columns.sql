ALTER TABLE settlement_batches
    ADD COLUMN IF NOT EXISTS last_reason_code VARCHAR(64);

ALTER TABLE settlement_requests
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_settlement_batches_last_reason_code
    ON settlement_batches (last_reason_code);

CREATE INDEX IF NOT EXISTS idx_settlement_requests_reason_code
    ON settlement_requests (reason_code);
