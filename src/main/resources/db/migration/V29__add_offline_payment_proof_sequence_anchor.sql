ALTER TABLE offline_payment_proofs
    ADD COLUMN IF NOT EXISTS sequence_anchor_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS sequence_anchor_reason VARCHAR(96) NULL;

UPDATE offline_payment_proofs proof
SET sequence_anchor_at = COALESCE(proof.sequence_anchor_at, proof.verified_at, proof.consumed_at, proof.updated_at, proof.created_at),
    sequence_anchor_reason = COALESCE(
        proof.sequence_anchor_reason,
        CASE
            WHEN EXISTS (
                SELECT 1
                FROM settlement_requests request
                WHERE request.proof_id = proof.id
                  AND COALESCE((request.settlement_result ->> 'financiallyHonored')::boolean, FALSE)
            )
                THEN 'FINANCIAL_HONOR:' || COALESCE(proof.reason_code, 'REJECTED')
            WHEN proof.reason_code = 'SENDER_AUTH_NOT_COMPLETED'
                THEN 'NON_FINANCIAL:SENDER_AUTH_NOT_COMPLETED'
            WHEN proof.reason_code IN ('COUNTER_GAP', 'INVALID_GENESIS_COUNTER')
                THEN 'CHAIN_MARKER:' || proof.reason_code
            WHEN proof.status = 'SETTLED'
                THEN 'SETTLED'
            WHEN proof.verified_at IS NOT NULL
                THEN 'VERIFIED'
            ELSE proof.reason_code
        END
    )
WHERE proof.sequence_anchor_at IS NULL
  AND (
    proof.status = 'SETTLED'
    OR proof.verified_at IS NOT NULL
    OR proof.reason_code IN ('COUNTER_GAP', 'INVALID_GENESIS_COUNTER', 'SENDER_AUTH_NOT_COMPLETED')
    OR EXISTS (
        SELECT 1
        FROM settlement_requests request
        WHERE request.proof_id = proof.id
          AND COALESCE((request.settlement_result ->> 'financiallyHonored')::boolean, FALSE)
    )
  );

CREATE INDEX IF NOT EXISTS idx_offline_payment_proofs_sequence_anchor
    ON offline_payment_proofs (sender_device_id, counter DESC, created_at DESC)
    WHERE sequence_anchor_at IS NOT NULL;
