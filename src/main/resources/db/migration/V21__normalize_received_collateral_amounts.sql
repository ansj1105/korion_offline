WITH normalized AS (
    SELECT
        id,
        GREATEST(
            amount - COALESCE(
                CASE
                    WHEN jsonb_exists(raw_payload, 'fee')
                        AND raw_payload ->> 'fee' ~ '^[0-9]+(\.[0-9]+)?$'
                        THEN (raw_payload ->> 'fee')::NUMERIC
                    WHEN UPPER(COALESCE(raw_payload ->> 'token', raw_payload ->> 'assetCode', 'KORI')) = 'KORI'
                        THEN ROUND(amount * 0.001, 6)
                    ELSE 0
                END,
                0
            ),
            0
        ) AS receiver_amount
    FROM offline_payment_proofs
    WHERE amount > 0
      AND (
          received_unsettled_amount >= amount
          OR received_settled_amount >= amount
      )
)
UPDATE offline_payment_proofs
SET received_unsettled_amount = CASE
        WHEN offline_payment_proofs.received_unsettled_amount >= offline_payment_proofs.amount
            THEN normalized.receiver_amount
        ELSE offline_payment_proofs.received_unsettled_amount
    END,
    received_settled_amount = CASE
        WHEN offline_payment_proofs.received_settled_amount >= offline_payment_proofs.amount
            THEN normalized.receiver_amount
        ELSE offline_payment_proofs.received_settled_amount
    END,
    updated_at = NOW()
FROM normalized
WHERE offline_payment_proofs.id = normalized.id;
