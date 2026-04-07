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

- 상태: `1차 반영`
- 해야 할 일:
  - [x] ledger 결과에 `accountingSide=SENDER`, `receiverSettlementMode=EXTERNAL_HISTORY_SYNC` 명시
  - [x] `settlementModel=SENDER_LEDGER_PLUS_RECEIVER_HISTORY` 명시
  - [ ] receiver leg를 독립 원장 단계로 승격할지 여부 최종 결정
  - [x] `offline_pay`, `foxya_coin_service` 응답/문서에도 동일 의미 1차 반영

### LD-3. compensation / reconciliation 가시성

- 상태: `1차 반영`
- 해야 할 일:
  - [x] ledger 결과에 `ledgerOutcome=FINALIZED|COMPENSATED`, `duplicated` 노출
  - [x] reconciliation case 상태를 `offline_pay` settlement detail 응답에 노출
  - [x] admin/API 응답에서 compensation/reconciliation 연결값 1차 추가
  - [ ] `coin_manage` contract까지 reconciliation linkage를 동일 레벨로 올릴지 검토

### LD-4. 담보 direct path 정책

- 상태: `대기`
- 해야 할 일:
  - online collateral topup/release에 대해 queue-first 외 별도 direct path가 필요한지 검토

## 현재 결론

- 구현 가능한 항목은 `1차 반영` 기준 대부분 완료됐다.
- 원장/정산 기준으로 남은 실질 미완료는 아래 두 가지다.
  - `receiver leg`를 `coin_manage` 독립 원장 단계로 승격할지에 대한 정책 결정
  - `online collateral direct path`를 queue-first와 분리할지에 대한 정책 결정
