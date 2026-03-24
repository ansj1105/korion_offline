# Offline Failure Policy

## Failure Classes

- `TRANSPORT`
  - network unreachable
  - timeout
  - `502/503/504`
  - circuit open
- `AUTH`
  - internal api key
  - device/signature auth
- `BUSINESS`
  - insufficient balance
  - validation error
  - proof mismatch
  - expired
- `PARTIAL`
  - offline_pay accepted
  - external ledger/history incomplete
- `CONFLICT`
  - duplicate send
  - duplicate nonce/counter
  - chain conflict
- `SYSTEM`
  - db/schema/worker serialization/runtime failure

## User App Policy

- `TRANSPORT`
  - queue registration stays successful locally
  - keep local projection
  - auto retry after reconnect
- `BUSINESS`
  - final failure
  - remove from optimistic local projection
  - show explicit reason
- `PARTIAL`
  - show processing state
  - operator reconciliation path remains open
- `CONFLICT`
  - block direct retry
  - show conflict guidance
- `AUTH`, `SYSTEM`
  - show delayed processing
  - escalate to operator alert

## Admin Policy

- `TRANSPORT`
  - bulk retry allowed
- `PARTIAL`
  - reconciliation retry priority
- `SYSTEM`
  - operator review required
- `AUTH`
  - secret/binding review first
- `BUSINESS`
  - manual fix then retry
- `CONFLICT`
  - resolve/close only

## Retry and Dead-Letter

- `TRANSPORT`
  - auto retry delays: `1m -> 5m -> 15m -> 1h -> 6h`
  - max auto retries: `10`
- `PARTIAL`
  - auto retry delays: `5m -> 15m -> 1h -> 6h`
  - max auto retries: `6`
- `SYSTEM`
  - auto retry delays: `5m -> 15m -> 1h`
  - max auto retries: `3`
- `AUTH`
  - auto retry delays: `1m -> 5m`
  - max auto retries: `2`
- `BUSINESS`
  - no auto retry
  - dead-letter immediately
- `CONFLICT`
  - no auto retry
  - dead-letter immediately

## Large-Scale Queue Failure

- group alerts by `failureClass + reasonCode + syncTarget`
- stop bulk retry while the same circuit remains open
- show aggregated counts to users and admins
- allow bulk retry only for `TRANSPORT`
- allow bulk resolve only for `CONFLICT`
