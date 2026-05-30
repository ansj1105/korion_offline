-- Offline settlement consumes the local snapshot remaining amount only.
-- Earlier settlement handling also reduced locked_amount, which made the hub
-- total collateral look smaller than the online top-up principal.
UPDATE collateral_locks
SET locked_amount = locked_amount + (metadata ->> 'deductedAmount')::numeric,
    status = CASE
        WHEN status = 'RELEASED' THEN 'PARTIALLY_SETTLED'
        ELSE status
    END,
    metadata = COALESCE(metadata, '{}'::jsonb)
        || jsonb_build_object(
            'lockedAmountRestoredFromSettlementDeduction', true,
            'lockedAmountRestoredAt', now()::text
        ),
    updated_at = now()
WHERE metadata ? 'deductedAmount'
  AND (metadata ->> 'deductedAmount') ~ '^[0-9]+(\.[0-9]+)?$'
  AND (
      metadata ->> 'lastStatus' = 'SETTLED'
      OR metadata ->> 'aggregateSettlement' = 'true'
  )
  AND COALESCE((metadata ->> 'lockedAmountRestoredFromSettlementDeduction')::boolean, false) = false;
