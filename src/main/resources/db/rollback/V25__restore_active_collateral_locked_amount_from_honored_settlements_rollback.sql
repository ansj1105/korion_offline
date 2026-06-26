-- Manual rollback for V25 data correction.
-- Run only if the V25 aggregate locked_amount restoration must be reverted.
UPDATE collateral_locks
SET locked_amount = GREATEST(
        locked_amount - (metadata ->> 'lockedAmountRestoredAmountV25')::numeric,
        0
    ),
    metadata = (
        metadata
        - 'lockedAmountRestoredFromAggregateHonoredSettlementsV25'
        - 'lockedAmountRestoredAmountV25'
        - 'lockedAmountRestoredAtV25'
    ) || jsonb_build_object(
        'lockedAmountRestoredFromAggregateHonoredSettlementsV25RolledBack', true,
        'lockedAmountRestoredFromAggregateHonoredSettlementsV25RolledBackAt', now()::text
    ),
    updated_at = now()
WHERE COALESCE(metadata ->> 'lockedAmountRestoredFromAggregateHonoredSettlementsV25', 'false') = 'true'
  AND COALESCE(metadata ->> 'lockedAmountRestoredAmountV25', '') ~ '^[0-9]+(\.[0-9]+)?$';
