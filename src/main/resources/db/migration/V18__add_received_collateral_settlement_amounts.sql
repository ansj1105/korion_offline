ALTER TABLE offline_payment_proofs
    ADD COLUMN IF NOT EXISTS received_unsettled_amount NUMERIC(36, 8) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS received_settled_amount NUMERIC(36, 8) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS received_collateral_settlement_operation_id UUID NULL REFERENCES collateral_operations(id),
    ADD COLUMN IF NOT EXISTS received_collateral_settlement_reference_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS received_collateral_settled_at TIMESTAMPTZ NULL;

UPDATE offline_payment_proofs
SET received_unsettled_amount = amount
WHERE received_unsettled_amount = 0
  AND received_settled_amount = 0
  AND status = 'SETTLED';

WITH completed_received_topups AS (
    SELECT
        collateral_operations.id AS operation_id,
        collateral_operations.reference_id,
        collateral_operations.updated_at,
        regexp_replace(source_id.value, '^proof:', '') AS proof_id
    FROM collateral_operations
    CROSS JOIN LATERAL jsonb_array_elements_text(
        COALESCE(collateral_operations.metadata #> '{metadata,sourceReceivedItemIds}', '[]'::jsonb)
    ) AS source_id(value)
    WHERE collateral_operations.operation_type = 'TOPUP'
      AND collateral_operations.status = 'COMPLETED'
)
UPDATE offline_payment_proofs
SET received_settled_amount = offline_payment_proofs.received_settled_amount
        + offline_payment_proofs.received_unsettled_amount,
    received_unsettled_amount = 0,
    received_collateral_settlement_operation_id = completed_received_topups.operation_id,
    received_collateral_settlement_reference_id = completed_received_topups.reference_id,
    received_collateral_settled_at = COALESCE(
        offline_payment_proofs.received_collateral_settled_at,
        completed_received_topups.updated_at
    )
FROM completed_received_topups
WHERE offline_payment_proofs.id::text = completed_received_topups.proof_id
  AND offline_payment_proofs.received_unsettled_amount > 0;

CREATE INDEX IF NOT EXISTS idx_offline_payment_proofs_received_unsettled
    ON offline_payment_proofs (received_unsettled_amount)
    WHERE received_unsettled_amount > 0;

CREATE INDEX IF NOT EXISTS idx_offline_payment_proofs_received_settlement_operation
    ON offline_payment_proofs (received_collateral_settlement_operation_id)
    WHERE received_collateral_settlement_operation_id IS NOT NULL;
