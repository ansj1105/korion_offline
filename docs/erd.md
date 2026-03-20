3. DB schema + index + unique 설계 (실제 SQL)

아래는 PostgreSQL + Flyway 기준이다.
PRD에 있는 테이블을 기준으로 실제 운영에 필요한 제약조건을 붙였다.

3-1. enum type
CREATE TYPE device_status AS ENUM ('ACTIVE', 'REVOKED', 'FROZEN');
CREATE TYPE collateral_status AS ENUM ('ACTIVE', 'FROZEN', 'SETTLED', 'CLOSED');
CREATE TYPE settlement_batch_status AS ENUM ('CREATED', 'UPLOADED', 'VALIDATING', 'PARTIALLY_SETTLED', 'SETTLED', 'FAILED', 'CLOSED');
CREATE TYPE settlement_status AS ENUM ('SETTLED', 'REJECTED', 'CONFLICTED', 'EXPIRED', 'REFUNDED');
CREATE TYPE conflict_status AS ENUM ('OPEN', 'REVIEWING', 'RESOLVED');
CREATE TYPE uploader_type AS ENUM ('SENDER', 'RECEIVER');
3-2. devices
CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    public_key TEXT NOT NULL,
    key_version INTEGER NOT NULL CHECK (key_version >= 1),
    status device_status NOT NULL DEFAULT 'ACTIVE',
    platform VARCHAR(16) NOT NULL,
    device_model VARCHAR(128),
    os_version VARCHAR(64),
    app_version VARCHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_devices_device_key_version
    ON devices (device_id, key_version);

CREATE UNIQUE INDEX uk_devices_active_device_key
    ON devices (device_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_devices_user_id ON devices (user_id);
CREATE INDEX idx_devices_status ON devices (status);

설명:

device_id + key_version은 이력 보존

활성 기기는 한 시점에 하나만 허용

3-3. device_risk_profiles
CREATE TABLE device_risk_profiles (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    max_offline_amount NUMERIC(36, 18) NOT NULL CHECK (max_offline_amount >= 0),
    policy_version INTEGER NOT NULL CHECK (policy_version >= 1),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_device_risk_profiles_device_policy
    ON device_risk_profiles (device_id, policy_version);

CREATE INDEX idx_device_risk_profiles_user_id ON device_risk_profiles (user_id);
3-4. collateral_locks
CREATE TABLE collateral_locks (
    id BIGSERIAL PRIMARY KEY,
    collateral_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    amount NUMERIC(36, 18) NOT NULL CHECK (amount > 0),
    remaining_amount NUMERIC(36, 18) NOT NULL CHECK (remaining_amount >= 0),
    initial_state_root VARCHAR(128) NOT NULL,
    policy_version INTEGER NOT NULL CHECK (policy_version >= 1),
    status collateral_status NOT NULL DEFAULT 'ACTIVE',
    external_lock_id VARCHAR(128),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_collateral_locks_collateral_id
    ON collateral_locks (collateral_id);

CREATE INDEX idx_collateral_locks_user_id ON collateral_locks (user_id);
CREATE INDEX idx_collateral_locks_device_id ON collateral_locks (device_id);
CREATE INDEX idx_collateral_locks_status ON collateral_locks (status);
CREATE INDEX idx_collateral_locks_expires_at ON collateral_locks (expires_at);

CREATE UNIQUE INDEX uk_active_collateral_per_device
    ON collateral_locks (device_id, asset_code)
    WHERE status = 'ACTIVE';

설명:

PoC에서는 동일 기기/자산에 활성 담보 하나로 단순화

나중에 멀티 풀 허용 시 이 unique partial index만 바꾸면 됨

3-5. settlement_batches
CREATE TABLE settlement_batches (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL,
    uploader_type uploader_type NOT NULL,
    uploader_device_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    status settlement_batch_status NOT NULL DEFAULT 'CREATED',
    total_count INTEGER NOT NULL CHECK (total_count >= 1),
    accepted_count INTEGER NOT NULL DEFAULT 0 CHECK (accepted_count >= 0),
    failed_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_settlement_batches_batch_id
    ON settlement_batches (batch_id);

CREATE UNIQUE INDEX uk_settlement_batches_idempotency_key
    ON settlement_batches (idempotency_key);

CREATE INDEX idx_settlement_batches_status ON settlement_batches (status);
CREATE INDEX idx_settlement_batches_uploader_device_id ON settlement_batches (uploader_device_id);

설명:

request_hash를 저장해서 같은 idempotency key인데 payload가 다른 경우 탐지

3-6. offline_payment_proofs
CREATE TABLE offline_payment_proofs (
    id BIGSERIAL PRIMARY KEY,
    proof_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64) NOT NULL,
    voucher_id VARCHAR(64) NOT NULL,
    collateral_id VARCHAR(64) NOT NULL,
    sender_device_id VARCHAR(64) NOT NULL,
    receiver_device_id VARCHAR(64),
    key_version INTEGER NOT NULL CHECK (key_version >= 1),
    policy_version INTEGER NOT NULL CHECK (policy_version >= 1),
    counter BIGINT NOT NULL CHECK (counter >= 1),
    nonce VARCHAR(128) NOT NULL,
    previous_hash VARCHAR(128) NOT NULL,
    hash_chain_head VARCHAR(128) NOT NULL,
    amount NUMERIC(36, 18) NOT NULL CHECK (amount > 0),
    timestamp_ms BIGINT NOT NULL,
    expires_at_ms BIGINT NOT NULL,
    signature TEXT NOT NULL,
    canonical_payload TEXT,
    uploader_type uploader_type NOT NULL,
    raw_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_offline_payment_proofs_proof_id
    ON offline_payment_proofs (proof_id);

CREATE UNIQUE INDEX uk_offline_payment_proofs_voucher_id
    ON offline_payment_proofs (voucher_id);

CREATE UNIQUE INDEX uk_offline_payment_proofs_sender_nonce
    ON offline_payment_proofs (sender_device_id, nonce);

CREATE INDEX idx_offline_payment_proofs_collateral_counter
    ON offline_payment_proofs (collateral_id, counter);

CREATE INDEX idx_offline_payment_proofs_batch_id
    ON offline_payment_proofs (batch_id);

CREATE INDEX idx_offline_payment_proofs_sender_device_id
    ON offline_payment_proofs (sender_device_id);

CREATE INDEX idx_offline_payment_proofs_receiver_device_id
    ON offline_payment_proofs (receiver_device_id);

설명:

voucher_id는 전역 유니크

(sender_device_id, nonce)도 유니크

collateral_id + counter는 일부러 unique로 막지 않았다
이유: 충돌 proof도 저장해야 하기 때문

대신 충돌은 정산 단계에서 판정 후 conflict table에 기록

3-7. settlements
CREATE TABLE settlements (
    id BIGSERIAL PRIMARY KEY,
    settlement_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64) NOT NULL,
    voucher_id VARCHAR(64) NOT NULL,
    collateral_id VARCHAR(64) NOT NULL,
    sender_device_id VARCHAR(64) NOT NULL,
    receiver_device_id VARCHAR(64),
    status settlement_status NOT NULL,
    reason_code VARCHAR(64),
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    settled_amount NUMERIC(36, 18),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_settlements_settlement_id
    ON settlements (settlement_id);

CREATE UNIQUE INDEX uk_settlements_voucher_id
    ON settlements (voucher_id);

CREATE INDEX idx_settlements_batch_id ON settlements (batch_id);
CREATE INDEX idx_settlements_collateral_id ON settlements (collateral_id);
CREATE INDEX idx_settlements_status ON settlements (status);

설명:

동일 voucher_id는 정산 결과도 하나만 갖게 만듦

3-8. settlement_conflicts
CREATE TABLE settlement_conflicts (
    id BIGSERIAL PRIMARY KEY,
    conflict_id VARCHAR(64) NOT NULL,
    settlement_id VARCHAR(64),
    voucher_id VARCHAR(64) NOT NULL,
    collateral_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    conflict_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status conflict_status NOT NULL DEFAULT 'OPEN',
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_settlement_conflicts_conflict_id
    ON settlement_conflicts (conflict_id);

CREATE INDEX idx_settlement_conflicts_status ON settlement_conflicts (status);
CREATE INDEX idx_settlement_conflicts_collateral_id ON settlement_conflicts (collateral_id);
CREATE INDEX idx_settlement_conflicts_device_id ON settlement_conflicts (device_id);
CREATE INDEX idx_settlement_conflicts_voucher_id ON settlement_conflicts (voucher_id);