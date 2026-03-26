# Offline Idempotency And Replay Policy

## Goal
- 같은 오프라인 의도가 네트워크 재시도, 앱 재실행, worker replay 때문에 중복 반영되지 않게 한다.
- 중복은 `idempotency key`, `referenceId`, `batchId`, `workflow/saga reference` 축으로 막는다.

## Canonical Rules
- `foxya total KORI`는 사용자 총자산 canonical source다.
- `offline_pay snapshot`은 canonical source가 아니라 cached projection이다.
- `coin_manage ledger`는 총자산 source가 아니라 담보/락/정산 ledger다.

## App Queue Rules
- 앱은 queue item 생성 시점에만 식별자를 만든다.
- 재시도는 같은 queue item을 다시 sync한다.
- queue item 재시도 시 `batchId`/`Idempotency-Key`는 바뀌면 안 된다.

## Settlement Rules
- settlement는 `batchId`를 `Idempotency-Key`로 사용한다.
- 같은 `Idempotency-Key`와 같은 payload면 기존 결과를 재사용한다.
- 같은 `Idempotency-Key`인데 payload가 다르면 conflict로 간주한다.

## Collateral Rules
- collateral top-up/release도 `Idempotency-Key`를 받는다.
- `offline_pay`는 `referenceId`를 안정적으로 만들고, `collateral_operations.reference_id` unique index로 중복을 막는다.
- 같은 `referenceId` 재요청이면 신규 insert 대신 기존 operation을 재사용한다.

## Worker Rules
- worker/scheduler는 overlap 실행을 금지한다.
- replay 시에도 `referenceId`, `batchId`, `settlementId` 기준으로 재실행 안전해야 한다.
- 보정 worker는 tolerance/max adjustment 정책을 넘으면 자동 보정하지 않는다.

## Duplicate And Replay Signals
- `reconciliation case`
  - `duplicate send`
  - `replay`
  - `payload mismatch`
- `workflow state`
  - `FAILED`
  - `DEAD_LETTERED`
  - duplicate / replay 관련 `reasonCode`
- `conflict`
  - duplicate voucher
  - duplicate nonce/counter

## Admin Guidance
- 관리자 화면은 최소 아래를 같이 보여줘야 한다.
  - duplicate/replay signal count
  - failed/dead-letter workflow count
  - blocked saga count
  - reconciliation open count
- 운영자는 duplicate/replay 건을 manual retry 전에 먼저 확인한다.
