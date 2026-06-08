ALTER TABLE settlement_requests
    ADD COLUMN IF NOT EXISTS receiver_confirmation_deadline_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS receiver_confirmation_expired_at TIMESTAMPTZ NULL;

ALTER TABLE offline_payment_proofs
    ADD COLUMN IF NOT EXISTS post_final_conflict_scanned_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_settlement_requests_receiver_confirmation_due
    ON settlement_requests (receiver_confirmation_deadline_at)
    WHERE receiver_confirmation_deadline_at IS NOT NULL
      AND receiver_confirmation_expired_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_offline_payment_proofs_post_final_conflict_scan
    ON offline_payment_proofs (settled_at, updated_at, created_at)
    WHERE status = 'SETTLED'
      AND post_final_conflict_scanned_at IS NULL;

UPDATE settlement_requests
SET receiver_confirmation_deadline_at = offline_sagas.updated_at + INTERVAL '24 hours'
FROM offline_sagas
WHERE settlement_requests.id::text = offline_sagas.reference_id
  AND settlement_requests.receiver_confirmation_deadline_at IS NULL
  AND settlement_requests.receiver_confirmation_expired_at IS NULL
  AND offline_sagas.saga_type = 'SETTLEMENT'
  AND offline_sagas.status = 'PARTIALLY_APPLIED'
  AND offline_sagas.current_step = 'HISTORY_SYNCED'
  AND offline_sagas.payload_json @> '{"receiverHistoryPending":true}'::jsonb;
