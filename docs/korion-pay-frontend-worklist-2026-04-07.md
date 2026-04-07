# KORION PAY 프런트 작업리스트

작성일: 2026-04-07

레포:
- `coin_front`: `/Users/an/work/coin_front`

연관 문서:
- [korion-pay-implementation-audit-2026-04-07.md](/Users/an/work/offline_pay/docs/korion-pay-implementation-audit-2026-04-07.md)
- [korion-pay-implementation-backlog-2026-04-07.md](/Users/an/work/offline_pay/docs/korion-pay-implementation-backlog-2026-04-07.md)

## 우선순위

1. 온라인 결제 시 `총 담보금 + 사용 가능 금액` 동시 반영 UX 정리
2. Wallet-Pay 동기화 강화
3. 현장 검증과 서버 최종 판정 UX 분리
4. Receiver 정산 상태 UI 분리
5. TEE / secure storage 상태 설명 정리
6. 자동/수동 정산 사용자 UX 구분
7. 알림 전달 모델 정리

## 작업 항목

### FE-1. Asset Hub 상단 카드 수치/문구 정리

- 상태: `진행중`
- 목표:
  - `총 담보금`
  - `사용가능한 금액`
  두 값의 의미를 online/offline 정책에 맞게 표시
- 해야 할 일:
  - SENT 탭 하단 라벨을 `사용가능한 금액`으로 고정
  - 하단 값은 `remainingAmount` 기준으로 표시
  - online settlement 성공 직후 `lockedAmount`, `remainingAmount`를 같이 갱신하는 UX 정리
  - topup/release 도움말 문구에서 `추가 담보 전환 가능 금액` 표현 제거

### FE-2. Snapshot stale / refresh UX

- 상태: `대기`
- 목표:
  - Wallet 과 Pay가 항상 같아 보이도록 refresh 타이밍 강화
- 해야 할 일:
  - settlement success / collateral topup / release 직후 snapshot 강제 refresh
  - stale indicator 또는 마지막 갱신 시각 정리

### FE-3. 현장 검증 vs 최종 정산 상태 문구 분리

- 상태: `대기`
- 해야 할 일:
  - `현장 승인`, `정산 대기`, `정산 완료`, `정산 지연`, `정산 실패` 상태 라벨 통일
  - QR/NFC/BLE에서 `성공` 표현이 정산 완료와 혼동되지 않게 수정

### FE-4. Receiver 정산 상태 UI

- 상태: `대기`
- 해야 할 일:
  - sender/receiver 정산 상태를 한 화면에서 구분할 필요가 있는지 결정
  - settlement detail 응답 확장 시 receiver leg 상태 노출

### FE-5. Trust Center / Secure Storage 설명 정리

- 상태: `대기`
- 해야 할 일:
  - TEE, secure storage, local fallback 차이를 사용자 문구와 설정 화면에서 명확히 구분

### FE-6. 자동/수동 정산 UX

- 상태: `대기`
- 해야 할 일:
  - 수동 정산 버튼이 언제 보이는지 규칙 정리
  - 자동 정산 진행 중 상태와 수동 요청 상태 구분

### FE-7. 알림 로컬/서버 경계 정리

- 상태: `대기`
- 해야 할 일:
  - 결과 모달, 로컬 로그, 서버 알림 히스토리의 역할 분리

