ALTER TABLE collateral_locks
    ALTER COLUMN expires_at DROP NOT NULL;

UPDATE collateral_locks
SET expires_at = NULL
WHERE expires_at IS NOT NULL;
