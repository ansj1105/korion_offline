UPDATE collateral_locks
SET
    locked_amount = remaining_amount,
    updated_at = NOW()
WHERE locked_amount > remaining_amount;
