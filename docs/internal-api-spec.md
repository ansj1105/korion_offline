# Internal API Spec

상대 서비스 구현용 내부 API 초안이다. 헤더는 두 서비스 모두 `X-Internal-Api-Key`를 사용한다.

## 1. `coin_manage`

Base path:
- `/internal/offline-pay`

### 1.1 `POST /internal/offline-pay/collateral/lock`

Headers:
- `X-Internal-Api-Key: <secret>`
- `Content-Type: application/json`

Request:
- Contract: [CoinManageLockCollateralContract.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/contracts/internal/CoinManageLockCollateralContract.java)

Response `200`:
- Contract: [CoinManageLockCollateralResponseContract.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/contracts/internal/CoinManageLockCollateralResponseContract.java)
- Example:
```json
{
  "lockId": "lock_01JXYZ...",
  "status": "LOCKED"
}
```

Error:
- `400` invalid payload
- `409` policy/balance conflict
- `401` internal api key invalid

Idempotency:
- `referenceId + deviceId + policyVersion` 기준 중복 요청 재실행 안전해야 함

### 1.2 `POST /internal/offline-pay/settlements/finalize`

Headers:
- `X-Internal-Api-Key: <secret>`
- `Content-Type: application/json`

Request:
- Contract: [CoinManageFinalizeSettlementContract.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/contracts/internal/CoinManageFinalizeSettlementContract.java)

Required behavior:
- ledger journal/posting 생성
- audit log 생성
- outbox event 생성 가능
- `settlementId` 기준 idempotent

Response `200`:
- Contract: [InternalAckResponseContract.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/contracts/internal/InternalAckResponseContract.java)
- Example:
```json
{
  "status": "OK",
  "message": "settlement finalized"
}
```

Error:
- `400` invalid payload
- `404` collateral or user context missing
- `409` already finalized with incompatible payload
- `401` internal api key invalid

## 2. `fox_coin`

Base path:
- `/internal/offline-pay`

### 2.1 `POST /internal/offline-pay/settlements/history`

Headers:
- `X-Internal-Api-Key: <secret>`
- `Content-Type: application/json`

Request:
- Contract: [FoxCoinRecordSettlementHistoryContract.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/contracts/internal/FoxCoinRecordSettlementHistoryContract.java)

Required behavior:
- `internal_transfers` 또는 해당 서비스의 canonical history write path에 반영
- 필요 시 wallet balance 반영
- `settlementId` 기준 idempotent
- `historyType=OFFLINE_PAY_SETTLEMENT|OFFLINE_PAY_CONFLICT` 허용

Response `200`:
- Contract: [InternalAckResponseContract.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/contracts/internal/InternalAckResponseContract.java)
- Example:
```json
{
  "status": "OK",
  "message": "history recorded"
}
```

Error:
- `400` invalid payload
- `409` duplicate settlement history with different payload
- `401` internal api key invalid

## 3. Retry Rule

- `offline_pay`는 timeout 또는 5xx만 재시도한다.
- 4xx는 비재시도이며 conflict/audit 대상으로 남긴다.
- 두 외부 서비스 모두 `settlementId` idempotency를 반드시 보장해야 한다.
