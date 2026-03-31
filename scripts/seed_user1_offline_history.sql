BEGIN;

DELETE FROM offline_payment_proofs
WHERE id IN (
    '70000000-0000-0000-0000-000000000001',
    '70000000-0000-0000-0000-000000000002',
    '70000000-0000-0000-0000-000000000003'
);

DELETE FROM settlement_batches
WHERE id IN (
    '60000000-0000-0000-0000-000000000001',
    '60000000-0000-0000-0000-000000000002',
    '60000000-0000-0000-0000-000000000003'
);

DELETE FROM collateral_operations
WHERE id IN (
    '50000000-0000-0000-0000-000000000001',
    '50000000-0000-0000-0000-000000000002'
);

DELETE FROM collateral_locks
WHERE id IN (
    '30000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001'
);

DELETE FROM devices
WHERE id IN (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001'
);

INSERT INTO devices (id, device_id, user_id, public_key, key_version, status, metadata)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'seed-user1-phone', 1, 'seed-public-key-user1-phone', 1, 'ACTIVE', '{"nickname":"User1 Seed Phone","platform":"ANDROID"}'::jsonb),
    ('10000000-0000-0000-0000-000000000002', 'seed-user1-tablet', 1, 'seed-public-key-user1-tablet', 1, 'ACTIVE', '{"nickname":"User1 Seed Tablet","platform":"ANDROID"}'::jsonb),
    ('20000000-0000-0000-0000-000000000001', 'seed-user2-store', 2, 'seed-public-key-user2-store', 1, 'ACTIVE', '{"nickname":"Seed Store POS","platform":"ANDROID"}'::jsonb);

INSERT INTO collateral_locks (
    id,
    user_id,
    device_id,
    asset_code,
    locked_amount,
    remaining_amount,
    initial_state_root,
    policy_version,
    status,
    external_lock_id,
    expires_at,
    metadata,
    created_at,
    updated_at
)
VALUES
    (
        '30000000-0000-0000-0000-000000000001',
        1,
        'seed-user1-phone',
        'KORI',
        50.00000000,
        34.00000000,
        'seed-state-root-user1',
        1,
        'ACTIVE',
        'seed-lock-user1',
        NOW() + INTERVAL '30 days',
        '{"description":"User 1 seed collateral"}'::jsonb,
        NOW() - INTERVAL '5 days',
        NOW() - INTERVAL '2 hours'
    ),
    (
        '40000000-0000-0000-0000-000000000001',
        2,
        'seed-user2-store',
        'KORI',
        20.00000000,
        13.00000000,
        'seed-state-root-user2',
        1,
        'ACTIVE',
        'seed-lock-user2',
        NOW() + INTERVAL '30 days',
        '{"description":"User 2 seed collateral"}'::jsonb,
        NOW() - INTERVAL '4 days',
        NOW() - INTERVAL '90 minutes'
    );

INSERT INTO collateral_operations (
    id,
    collateral_id,
    user_id,
    device_id,
    asset_code,
    operation_type,
    amount,
    status,
    reference_id,
    metadata,
    created_at,
    updated_at
)
VALUES
    (
        '50000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        1,
        'seed-user1-phone',
        'KORI',
        'TOPUP',
        50.00000000,
        'COMPLETED',
        'seed-user1-topup',
        '{"description":"오프라인 담보 충전"}'::jsonb,
        NOW() - INTERVAL '5 days',
        NOW() - INTERVAL '5 days'
    ),
    (
        '50000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000001',
        1,
        'seed-user1-phone',
        'KORI',
        'RELEASE',
        8.00000000,
        'COMPLETED',
        'seed-user1-release',
        '{"description":"오프라인 담보 해제"}'::jsonb,
        NOW() - INTERVAL '1 day',
        NOW() - INTERVAL '1 day'
    );

INSERT INTO settlement_batches (
    id,
    source_device_id,
    idempotency_key,
    status,
    last_reason_code,
    proofs_count,
    summary,
    created_at,
    updated_at
)
VALUES
    (
        '60000000-0000-0000-0000-000000000001',
        'seed-user1-phone',
        'seed-user1-batch-send-1',
        'SETTLED',
        'SETTLED',
        1,
        '{"history":"seed"}'::jsonb,
        NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '3 days'
    ),
    (
        '60000000-0000-0000-0000-000000000002',
        'seed-user1-phone',
        'seed-user1-batch-send-2',
        'SETTLED',
        'SETTLED',
        1,
        '{"history":"seed"}'::jsonb,
        NOW() - INTERVAL '2 days',
        NOW() - INTERVAL '2 days'
    ),
    (
        '60000000-0000-0000-0000-000000000003',
        'seed-user2-store',
        'seed-user2-batch-receive-1',
        'SETTLED',
        'SETTLED',
        1,
        '{"history":"seed"}'::jsonb,
        NOW() - INTERVAL '12 hours',
        NOW() - INTERVAL '12 hours'
    );

INSERT INTO offline_payment_proofs (
    id,
    batch_id,
    voucher_id,
    collateral_id,
    sender_device_id,
    receiver_device_id,
    key_version,
    policy_version,
    counter,
    nonce,
    hash_chain_head,
    previous_hash,
    signature,
    amount,
    payload,
    timestamp_ms,
    expires_at_ms,
    canonical_payload,
    uploader_type,
    channel_type,
    status,
    reason_code,
    raw_payload,
    issued_at,
    uploaded_at,
    consumed_at,
    verified_at,
    settled_at,
    created_at,
    updated_at
)
VALUES
    (
        '70000000-0000-0000-0000-000000000001',
        '60000000-0000-0000-0000-000000000001',
        'seed-user1-proof-send-1',
        '30000000-0000-0000-0000-000000000001',
        'seed-user1-phone',
        'seed-user2-store',
        1,
        1,
        1,
        'seed-nonce-send-1',
        'seed-hash-send-1',
        'seed-prev-send-0',
        'seed-signature-send-1',
        5.00000000,
        '{"token":"KORI","network":"mainnet","counterparty":"Seed Store POS","description":"매장 오프라인 결제","category":"STORE","paymentMethod":"NFC","fee":"0.000000"}'::jsonb,
        EXTRACT(EPOCH FROM (NOW() - INTERVAL '3 days'))::bigint * 1000,
        EXTRACT(EPOCH FROM (NOW() + INTERVAL '10 days'))::bigint * 1000,
        '{"seed":true}'::text,
        'SENDER',
        'NFC',
        'SETTLED',
        'SETTLED',
        '{"token":"KORI","network":"mainnet","counterparty":"Seed Store POS","description":"매장 오프라인 결제","category":"STORE","paymentMethod":"NFC","fee":"0.000000"}'::jsonb,
        NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '3 days'
    ),
    (
        '70000000-0000-0000-0000-000000000002',
        '60000000-0000-0000-0000-000000000002',
        'seed-user1-proof-send-2',
        '30000000-0000-0000-0000-000000000001',
        'seed-user1-phone',
        'seed-user1-tablet',
        1,
        1,
        2,
        'seed-nonce-send-2',
        'seed-hash-send-2',
        'seed-prev-send-1',
        'seed-signature-send-2',
        3.00000000,
        '{"token":"KORI","network":"mainnet","counterparty":"User1 Seed Tablet","description":"테스트 단말 송금","category":"P2P","paymentMethod":"BLE","fee":"0.000000"}'::jsonb,
        EXTRACT(EPOCH FROM (NOW() - INTERVAL '2 days'))::bigint * 1000,
        EXTRACT(EPOCH FROM (NOW() + INTERVAL '10 days'))::bigint * 1000,
        '{"seed":true}'::text,
        'SENDER',
        'BLE',
        'SETTLED',
        'SETTLED',
        '{"token":"KORI","network":"mainnet","counterparty":"User1 Seed Tablet","description":"테스트 단말 송금","category":"P2P","paymentMethod":"BLE","fee":"0.000000"}'::jsonb,
        NOW() - INTERVAL '2 days',
        NOW() - INTERVAL '2 days',
        NOW() - INTERVAL '2 days',
        NOW() - INTERVAL '2 days',
        NOW() - INTERVAL '2 days',
        NOW() - INTERVAL '2 days',
        NOW() - INTERVAL '2 days'
    ),
    (
        '70000000-0000-0000-0000-000000000003',
        '60000000-0000-0000-0000-000000000003',
        'seed-user2-proof-receive-1',
        '40000000-0000-0000-0000-000000000001',
        'seed-user2-store',
        'seed-user1-phone',
        1,
        1,
        1,
        'seed-nonce-receive-1',
        'seed-hash-receive-1',
        'seed-prev-receive-0',
        'seed-signature-receive-1',
        7.00000000,
        '{"token":"KORI","network":"mainnet","counterparty":"Seed Store POS","description":"오프라인 수취 테스트","category":"STORE","paymentMethod":"QR","fee":"0.000000"}'::jsonb,
        EXTRACT(EPOCH FROM (NOW() - INTERVAL '12 hours'))::bigint * 1000,
        EXTRACT(EPOCH FROM (NOW() + INTERVAL '10 days'))::bigint * 1000,
        '{"seed":true}'::text,
        'SENDER',
        'QR',
        'SETTLED',
        'SETTLED',
        '{"token":"KORI","network":"mainnet","counterparty":"Seed Store POS","description":"오프라인 수취 테스트","category":"STORE","paymentMethod":"QR","fee":"0.000000"}'::jsonb,
        NOW() - INTERVAL '12 hours',
        NOW() - INTERVAL '12 hours',
        NOW() - INTERVAL '12 hours',
        NOW() - INTERVAL '12 hours',
        NOW() - INTERVAL '12 hours',
        NOW() - INTERVAL '12 hours',
        NOW() - INTERVAL '12 hours'
    );

COMMIT;
