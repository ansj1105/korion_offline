ALTER TABLE offline_payment_proofs
    ADD COLUMN IF NOT EXISTS issued_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMPTZ NULL;

UPDATE offline_payment_proofs
SET issued_at = COALESCE(issued_at, created_at)
WHERE issued_at IS NULL;
