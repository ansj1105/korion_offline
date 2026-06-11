UPDATE offline_payment_proofs
SET received_unsettled_amount = amount,
    updated_at = NOW()
WHERE status = 'SETTLED'
  AND amount > 0
  AND received_unsettled_amount = 0
  AND received_settled_amount = 0;
