# KORION PAY 서버 작업리스트

작성일: 2026-04-07

레포:
- `offline_pay`: `/Users/an/work/offline_pay`
- `foxya_coin_service`: `/Users/an/work/foxya_coin_service`

연관 문서:
- [korion-pay-implementation-audit-2026-04-07.md](/Users/an/work/offline_pay/docs/korion-pay-implementation-audit-2026-04-07.md)
- [korion-pay-implementation-backlog-2026-04-07.md](/Users/an/work/offline_pay/docs/korion-pay-implementation-backlog-2026-04-07.md)

## 우선순위

1. settlement detail contract 강화
2. Receiver 정산 구조 명확화
3. trust / TEE contract 명확화
4. snapshot refresh hint 제공
5. 자동/수동 정산 정책 명시
6. 알림/히스토리 모델 정리

## 작업 항목

### BE-1. Settlement detail 응답 강화

- 상태: `대기`
- 레포: `offline_pay`
- 해야 할 일:
  - `postSettlementLockedAmount`, `postSettlementRemainingAmount` 또는 동등한 반영값 제공 검토
  - sender/receiver leg 상태를 응답에 넣을지 결정
  - 현장 검증 통과와 서버 최종 판정 상태를 구분하는 reason/status 정리

### BE-2. Receiver 정산 구조 확정

- 상태: `대기`
- 레포:
  - `offline_pay`
  - `foxya_coin_service`
- 해야 할 일:
  - sender proof 단일 권위 모델 유지 여부 결정
  - receiver ack/proof leg 도입 여부 결정
  - historyType / settlement logs 의미 통일

### BE-3. Trust / TEE 최소 계약 정의

- 상태: `대기`
- 레포:
  - `offline_pay`
  - `foxya_coin_service`
- 해야 할 일:
  - 서버가 요구하는 최소 trust contract 문서화
  - trust center DTO에 보안 수준 설명 필드 보강 검토

### BE-4. Snapshot refresh hint

- 상태: `대기`
- 레포:
  - `offline_pay`
  - `foxya_coin_service`
- 해야 할 일:
  - current snapshot 응답에 watermark/version/refresh hint 추가 검토

### BE-5. 정산 정책 명시

- 상태: `대기`
- 레포: `offline_pay`
- 해야 할 일:
  - 자동 정산, 수동 정산, 리컨실 개입 조건을 문서와 상태값으로 명확화

### BE-6. 사용자 알림 / 운영 알림 경계

- 상태: `대기`
- 레포:
  - `offline_pay`
  - `foxya_coin_service`
- 해야 할 일:
  - settlement/history/notification 로그의 책임 분리

