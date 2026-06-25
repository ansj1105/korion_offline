ALTER TABLE offline_payment_proofs
    ADD COLUMN IF NOT EXISTS sender_user_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS receiver_user_id BIGINT NULL;

UPDATE offline_payment_proofs proof
SET sender_user_id = collateral.user_id
FROM collateral_locks collateral
WHERE collateral.id = proof.collateral_id
  AND proof.sender_user_id IS NULL;

UPDATE offline_payment_proofs
SET receiver_user_id = (raw_payload ->> 'receiverUserId')::BIGINT
WHERE receiver_user_id IS NULL
  AND raw_payload ? 'receiverUserId'
  AND raw_payload ->> 'receiverUserId' ~ '^[0-9]+$';

CREATE INDEX IF NOT EXISTS idx_offline_payment_proofs_sender_user
    ON offline_payment_proofs (sender_user_id, created_at DESC)
    WHERE sender_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_offline_payment_proofs_receiver_user
    ON offline_payment_proofs (receiver_user_id, created_at DESC)
    WHERE receiver_user_id IS NOT NULL;
