# KORION PAY 원장/정산 작업리스트

작성일: 2026-04-07

레포:
- `coin_manage`: `/Users/an/work/coin_manage`

연관 문서:
- [korion-pay-implementation-audit-2026-04-07.md](/Users/an/work/offline_pay/docs/korion-pay-implementation-audit-2026-04-07.md)
- [korion-pay-implementation-backlog-2026-04-07.md](/Users/an/work/offline_pay/docs/korion-pay-implementation-backlog-2026-04-07.md)

## 우선순위

1. finalize 결과를 프런트 snapshot과 더 빨리 맞출 수 있게 하기
2. sender/receiver accounting 책임 명확화
3. compensation / reconciliation 가시성 강화
4. 온라인 담보 direct path 지원 여부 검토

## 작업 항목

### LD-1. finalize 후 snapshot 반영 기준 강화

- 상태: `1차 반영`
- 해야 할 일:
  - [x] finalize/compensate 결과에 `postAvailableBalance`, `postLockedBalance`, `postOfflinePayPendingBalance` 노출
  - [x] `/settlements/finalize`, `/settlements/compensate` 내부 API 응답 contract 확장
  - [x] 이 값을 `offline_pay` settlement detail 또는 snapshot refresh 힌트와 직접 연결

### LD-2. sender / receiver accounting 책임 명확화

- 상태: `정책 확정`
- 해야 할 일:
  - [x] ledger 결과에 `accountingSide=SENDER`, `receiverSettlementMode=EXTERNAL_HISTORY_SYNC` 명시
  - [x] `settlementModel=SENDER_LEDGER_PLUS_RECEIVER_HISTORY` 명시
  - [x] receiver leg를 `coin_manage` 독립 원장으로 승격하지 않음 — sender collateral ledger + receiver history 구조 유지
  - [x] `offline_pay`, `foxya_coin_service` 응답/문서에도 동일 의미 1차 반영

### LD-3. compensation / reconciliation 가시성

- 상태: `반영`
- 해야 할 일:
  - [x] ledger 결과에 `ledgerOutcome=FINALIZED|COMPENSATED`, `duplicated` 노출
  - [x] reconciliation case 상태를 `offline_pay` settlement detail 응답에 노출
  - [x] admin/API 응답에서 compensation/reconciliation 연결값 1차 추가
  - [x] `coin_manage` contract까지 reconciliation linkage(`reconciliationTrackingOwner=OFFLINE_PAY_SAGA`)를 동일 레벨로 반영
  - [x] receiver history 실패 시 `LEDGER_COMPENSATION_REQUESTED`와 `OFFLINE_PAY_COMPENSATION` foxya history를 함께 발행해 sender 원장/사용자 내역을 되돌림

### LD-4. 담보 direct path 정책

- 상태: `정책 확정`
- 해야 할 일:
  - [x] online collateral topup/release는 `QUEUE_FIRST_IMMEDIATE_SYNC` 유지 — 별도 direct path 미도입

### LD-5. receiver 수취금 담보 전환 정책

- 상태: `정책 추가`
- 해야 할 일:
  - [x] receiver 수취금은 settlement 성공 시 즉시 `receiver collateral`로 직접 편입하지 않는다.
  - [x] receiver 수취금은 먼저 `foxya user_wallets.balance`와 `OFFLINE_PAY_RECEIVE` history에 반영한다.
  - [x] 사용자가 받은쪽 `Settle now` 또는 `Auto settle`을 실행하면, 완료된 수취 합계를 기존 `COLLATERAL_TOPUP` 큐로 등록한다.
  - [x] 담보 전환은 기존 담보 충전 경로와 동일하게 `offline_pay -> coin_manage collateral lock`으로 처리한다.
  - [x] 중복 전환 방지를 위해 앱은 담보 전환 요청에 사용한 received item id를 로컬 consumed marker로 관리한다.

## 현재 결론

- 구현 가능한 항목은 `1차 반영` 기준 대부분 완료됐다.
- 구현 가능한 항목은 완료됐다.
- 원장/정산 기준 남은 미완료는 없다. 단, receiver 수취금을 담보로 쓰려면 자동 편입이 아니라 명시적 담보 전환 요청을 거친다.
- 현재 정책 고정값:
  - `receiver leg`는 `coin_manage` 독립 원장으로 올리지 않고 `SENDER_LEDGER_PLUS_RECEIVER_HISTORY` 유지
  - online collateral topup/release는 별도 direct path 없이 `QUEUE_FIRST_IMMEDIATE_SYNC` 유지
  - receiver 수취금 담보 전환은 `OFFLINE_PAY_RECEIVE -> COLLATERAL_TOPUP` 2단계 모델 유지
