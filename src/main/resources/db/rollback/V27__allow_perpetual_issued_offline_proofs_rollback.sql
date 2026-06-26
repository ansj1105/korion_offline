UPDATE issued_offline_proofs
SET expires_at = COALESCE(expires_at, NOW() + INTERVAL '100 years')
WHERE expires_at IS NULL;

ALTER TABLE issued_offline_proofs
    ALTER COLUMN expires_at SET NOT NULL;
