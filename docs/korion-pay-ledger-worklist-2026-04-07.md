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

- 상태: `대기`
- 해야 할 일:
  - finalize 결과가 snapshot API 또는 상위 서비스에 빠르게 반영될 수 있는 기준값 제공 검토
  - `locked/remaining` 후속 상태를 외부에 전달할 contract 검토

### LD-2. sender / receiver accounting 책임 명확화

- 상태: `대기`
- 해야 할 일:
  - receiver 자산 반영이 독립 원장 단계인지, sender finalize의 결과인지 문서/코드 정리

### LD-3. compensation / reconciliation 가시성

- 상태: `대기`
- 해야 할 일:
  - compensate/finalize 결과를 상위 서비스가 더 쉽게 추적할 수 있는 상태값 검토

### LD-4. 담보 direct path 정책

- 상태: `대기`
- 해야 할 일:
  - online collateral topup/release에 대해 queue-first 외 별도 direct path가 필요한지 검토
