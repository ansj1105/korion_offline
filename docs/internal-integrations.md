# Internal Integration Contracts

`offline_pay`는 외부 서비스 DB에 직접 쓰지 않는다. 내부 API contract를 통해 `coin_manage`와 `fox_coin`에 기록을 남긴다.

## 1. `offline_pay -> coin_manage`

`coin_manage`는 canonical ledger와 audit/outbox 책임을 가진다.

### 1.1 담보 잠금

- Route: `POST /internal/offline-pay/collateral/lock`
- Contract: [CoinManageLockCollateralContract.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/contracts/internal/CoinManageLockCollateralContract.java)
- Purpose:
  - user 자산 잠금
  - `external_lock_id` 발급
  - `offline_pay` collateral 생성 전 선행 검증

Request fields:
- `userId`
- `deviceId`
- `assetCode`
- `amount`
- `referenceId`
- `policyVersion`

Expected response:
- `lockId`
- `status`

### 1.2 정산 원장 finalize

- Route: `POST /internal/offline-pay/settlements/finalize`
- Contract: [CoinManageFinalizeSettlementContract.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/contracts/internal/CoinManageFinalizeSettlementContract.java)
- Purpose:
  - `coin_manage` ledger journal/posting 기록
  - audit log/outbox 이벤트 생성
  - release 또는 adjust 결정 반영

Request fields:
- `settlementId`
- `batchId`
- `collateralId`
- `proofId`
- `userId`
- `deviceId`
- `assetCode`
- `amount`
- `settlementStatus`
- `releaseAction`
- `conflictDetected`

Implementation note:
- 기존 `withdrawals` API에 억지로 매핑하지 말고 `offline_pay settlement posting` 전용 내부 adapter/API를 `coin_manage` 쪽에 추가하는 것이 맞다.

## 2. `offline_pay -> fox_coin`

`fox_coin`은 사용자 앱 거래내역과 표시용 history 책임을 가진다.

### 2.1 거래내역 기록

- Route: `POST /internal/offline-pay/settlements/history`
- Contract: [FoxCoinRecordSettlementHistoryContract.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/contracts/internal/FoxCoinRecordSettlementHistoryContract.java)
- Purpose:
  - 앱 거래내역 노출용 record 생성
  - `internal_transfers` 또는 이에 준하는 내부 write path 반영
  - 필요 시 wallet balance 반영과 같은 자체 트랜잭션 처리

Request fields:
- `settlementId`
- `batchId`
- `collateralId`
- `proofId`
- `userId`
- `deviceId`
- `assetCode`
- `amount`
- `settlementStatus`
- `historyType`

Implementation note:
- `offline_pay`가 `coin_system_cloud` DB에 직접 쓰지 않는다.
- `fox_coin` 내부 API에서 `internal_transfers`와 잔액 갱신 규칙을 소유해야 한다.

## 3. Ownership Rule

- 원장 정본: `coin_manage`
- 사용자 거래내역 정본: `fox_coin`
- proof/chain/conflict 정본: `offline_pay`

이 세 서비스는 같은 VPC 안 private 통신으로 연결하고, shared DB는 사용하지 않는다.
