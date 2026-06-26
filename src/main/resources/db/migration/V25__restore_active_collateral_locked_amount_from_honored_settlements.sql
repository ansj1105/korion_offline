-- Offline settlement spends remaining_amount, not the collateral principal.
-- V16 restored rows that had a single deductedAmount marker at that time. Later
-- buggy settlements could reduce locked_amount again while V16 was already marked
-- as applied. Rebuild the aggregate active locked gap from server-honored proofs:
--   expected active locked gap = honored settlement amount
--   current active locked gap  = SUM(locked_amount) - SUM(remaining_amount)
-- Restore only the missing positive delta once per user/asset.
WITH honored_settlements AS (
    SELECT
        collateral_locks.user_id,
        collateral_locks.asset_code,
        COALESCE(SUM(offline_payment_proofs.amount), 0)::numeric(36, 8) AS honored_amount
    FROM settlement_requests
    JOIN offline_payment_proofs
      ON offline_payment_proofs.id = settlement_requests.proof_id
    JOIN collateral_locks
      ON collateral_locks.id = settlement_requests.collateral_id
    WHERE settlement_requests.status = 'SETTLED'
       OR COALESCE(settlement_requests.settlement_result ->> 'financiallyHonored', 'false') = 'true'
    GROUP BY collateral_locks.user_id, collateral_locks.asset_code
),
active_collateral AS (
    SELECT
        user_id,
        asset_code,
        COALESCE(SUM(locked_amount), 0)::numeric(36, 8) AS locked_amount,
        COALESCE(SUM(remaining_amount), 0)::numeric(36, 8) AS remaining_amount
    FROM collateral_locks
    WHERE status IN ('LOCKED', 'PARTIALLY_SETTLED')
    GROUP BY user_id, asset_code
),
restore_delta AS (
    SELECT
        active_collateral.user_id,
        active_collateral.asset_code,
        (
            honored_settlements.honored_amount
            - GREATEST(active_collateral.locked_amount - active_collateral.remaining_amount, 0)
        )::numeric(36, 8) AS restore_amount
    FROM active_collateral
    JOIN honored_settlements
      ON honored_settlements.user_id = active_collateral.user_id
     AND honored_settlements.asset_code = active_collateral.asset_code
    WHERE honored_settlements.honored_amount
          > GREATEST(active_collateral.locked_amount - active_collateral.remaining_amount, 0)
),
restore_target AS (
    SELECT DISTINCT ON (collateral_locks.user_id, collateral_locks.asset_code)
        collateral_locks.id,
        restore_delta.restore_amount
    FROM collateral_locks
    JOIN restore_delta
      ON restore_delta.user_id = collateral_locks.user_id
     AND restore_delta.asset_code = collateral_locks.asset_code
    WHERE collateral_locks.status IN ('LOCKED', 'PARTIALLY_SETTLED')
      AND restore_delta.restore_amount > 0
      AND COALESCE(collateral_locks.metadata ->> 'lockedAmountRestoredFromAggregateHonoredSettlementsV25', 'false') <> 'true'
    ORDER BY
        collateral_locks.user_id,
        collateral_locks.asset_code,
        collateral_locks.remaining_amount DESC,
        collateral_locks.updated_at DESC,
        collateral_locks.created_at DESC
)
UPDATE collateral_locks
SET locked_amount = collateral_locks.locked_amount + restore_target.restore_amount,
    metadata = COALESCE(collateral_locks.metadata, '{}'::jsonb)
        || jsonb_build_object(
            'lockedAmountRestoredFromAggregateHonoredSettlementsV25', true,
            'lockedAmountRestoredAmountV25', restore_target.restore_amount::text,
            'lockedAmountRestoredAtV25', now()::text
        ),
    updated_at = now()
FROM restore_target
WHERE collateral_locks.id = restore_target.id;
