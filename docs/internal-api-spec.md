# Internal API Spec

상대 서비스 구현용 내부 API 초안이다. 헤더는 두 서비스 모두 `X-Internal-Api-Key`를 사용한다.

## 1. `coin_manage`

Base path:
- `/api/internal/offline-pay`

### 1.1 `POST /api/internal/offline-pay/collateral/lock`

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

### 1.2 `POST /api/internal/offline-pay/settlements/finalize`

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
- `financiallyHonored=true`는 로컬 검증 완료 후 `PENDING`으로 서버 검증에 진입한 거래가 서버 검증에서 `REJECTED` 되었지만 정책상 금전 반영을 유지할 때만 허용한다.
- 이 경우 요청은 `settlementStatus=REJECTED`, `releaseAction=RELEASE`, `conflictDetected=false`를 유지해야 하며, 서버 검증 실패 사유는 audit/reconciliation detail에 남긴다.

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

### 1.3 `GET /api/internal/offline-pay/users/{userId}/pending-balance`

Headers:
- `X-Internal-Api-Key: <secret>`

Query:
- `assetCode`: default `KORI`

Required behavior:
- `coin_manage`의 canonical `offline_pay_pending` 원장 잔액을 반환한다.
- `korion_offline`의 active collateral `remaining_amount`와 비교하는 reconciliation 전용 API다.
- `total collateral`이나 Foxya wallet balance를 대신 반환하면 안 된다.

Response `200`:
- Contract: [CoinManagePendingBalanceResponseContract.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/contracts/internal/CoinManagePendingBalanceResponseContract.java)
- Example:
```json
{
  "status": "OK",
  "userId": "35",
  "assetCode": "KORI",
  "offlinePayPendingBalance": "494.000000"
}
```

Error:
- `401` internal api key invalid

## 2. `fox_coin`

Base path:
- `/api/v1/internal/offline-pay`

### 2.1 `POST /api/v1/internal/offline-pay/settlements/history`

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

## 4. Dead Letter Requeue Rule

- `DEAD_LETTER`는 DB 값을 직접 수정하지 않고 admin/ops API로만 재처리한다.
- 일반 retryable case는 `POST /api/admin/anomalies/reconciliation-cases/{caseId}/retry`를 사용한다.
- `OFFLINE_PAY_FEE_MISMATCH`처럼 서비스 간 계약 오류로 발생한 case는 계약 수정 및 배포가 끝난 뒤 `POST /api/admin/anomalies/reconciliation-cases/{caseId}/retry-contract-fixed`를 사용한다.
- `retry-contract-fixed`는 `LEDGER_SYNC_FAILED` + `OFFLINE_PAY_FEE_MISMATCH` 이력만 허용하고, 원래 payload를 그대로 재발행한다.
- `POST_FINAL_PROOF_CONFLICT`는 자동 보상하지 않는다. 운영자가 증거를 확인한 뒤 아래 admin API 중 하나로 종결한다.
  - `POST /api/admin/anomalies/reconciliation-cases/{caseId}/request-compensation`
  - `POST /api/admin/anomalies/reconciliation-cases/{caseId}/resolve-no-compensation`
  - `POST /api/admin/anomalies/reconciliation-cases/{caseId}/close`
- 세 API는 우선 `POST_FINAL_PROOF_CONFLICT` + `OPEN` case만 허용한다.
- 요청 body는 선택적으로 `operatorId`, `reason`을 포함한다.
- `request-compensation`은 즉시 금액을 이동하지 않고 `LEDGER_COMPENSATION_REQUESTED` event만 발행한다. event payload와 case detail에는 `manualCompensationIdempotencyKey=manual-compensation:{caseId}`가 기록되며 이미 보상 요청된 case는 재요청을 거절한다.
- 감사 필드는 case detail/응답에 `manualOperatorId`, `manualReason`, `manualActionExecutedAt`, `manualCompensationRequestedAt` 또는 `manualResolvedAt`/`manualClosedAt`, `manualCompensationIdempotencyKey`로 남긴다. `caseId`, `settlementId`, `proofId`는 reconciliation case 본문 필드로 함께 조회된다.
- 재처리 성공 여부는 outbox event, reconciliation case status, `coin_manage.offline_pay_pending`, `korion_offline.remaining_amount` 비교로 확인한다.
