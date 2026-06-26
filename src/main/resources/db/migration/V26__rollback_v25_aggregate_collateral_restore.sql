-- V25 restored active collateral locked_amount from aggregate honored
-- settlements. That aggregate basis was too broad for current active rows and
-- could overstate the user's collateral total after historical settlements.
-- Revert only rows explicitly marked by V25. This is idempotent: environments
-- already manually rolled back or never applied V25 are unaffected.
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
        'lockedAmountRestoredFromAggregateHonoredSettlementsV25RolledBackByV26', true,
        'lockedAmountRestoredFromAggregateHonoredSettlementsV25RolledBackByV26At', now()::text
    ),
    updated_at = now()
WHERE COALESCE(metadata ->> 'lockedAmountRestoredFromAggregateHonoredSettlementsV25', 'false') = 'true'
  AND COALESCE(metadata ->> 'lockedAmountRestoredAmountV25', '') ~ '^[0-9]+(\.[0-9]+)?$';
