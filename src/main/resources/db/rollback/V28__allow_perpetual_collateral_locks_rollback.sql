UPDATE collateral_locks
SET expires_at = COALESCE(expires_at, NOW() + INTERVAL '100 years')
WHERE expires_at IS NULL;

ALTER TABLE collateral_locks
    ALTER COLUMN expires_at SET NOT NULL;
