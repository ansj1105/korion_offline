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
  - [x] topup/release 도움말 문구에서 `추가 담보 전환 가능 금액` 표현 제거

### FE-2. Snapshot stale / refresh UX

- 상태: `진행중`
- 목표:
  - Wallet 과 Pay가 항상 같아 보이도록 refresh 타이밍 강화
- 해야 할 일:
  - [x] settlement success / compensation completed 직후 snapshot 강제 refresh
  - [x] collateral topup / release 직후 snapshot 강제 refresh 추가 보강
  - [x] stale indicator 또는 마지막 갱신 시각 1차 노출
  - [x] stale 상태 기준과 수동 새로고침 UX 추가 정리

### FE-3. 현장 검증 vs 최종 정산 상태 문구 분리

- 상태: `진행중`
- 해야 할 일:
  - [x] settlement detail의 `ledgerOutcome`, `post*Balance`를 읽어서 완료/보상/지연 문구를 더 정확히 표시
  - [x] settlement center log에 ledger 결과와 post-balance metadata를 같이 기록
  - [x] RECEIVE 성공 화면에서 `결제 성공` 대신 `수취 확인 완료` + `서버 정산 전 상태`로 정리
  - [x] fallback 완료 상태를 `정산 대기 중` 기준으로 정리
  - [x] 수취 완료 fallback 설명을 `수취 저장 + Wallet 정산 대기` 기준으로 정리
  - [x] 수신 알림/가이드에서 `성공 화면` 표현 제거
  - [x] confirm/success/failure fallback 문구를 `현장 승인 / 서버 정산` 기준으로 1차 통일
  - [x] `현장 승인`, `정산 대기`, `정산 완료`, `정산 지연`, `정산 실패` 상태 라벨 완전 통일
  - [x] QR/NFC/BLE에서 `성공` 표현이 정산 완료와 혼동되지 않게 전체 점검

### FE-4. Receiver 정산 상태 UI

- 상태: `진행중`
- 해야 할 일:
  - [x] sender/receiver 정산 상태 문구를 최소 범위로 분리
  - [x] receiver 정산 완료/지연/보상 상태를 Wallet 반영 기준 문구로 노출
  - [x] sender/receiver 정산 상태를 한 화면에서 더 명시적으로 구분할 필요가 있는지 결정
  - [x] 결과 화면에서 `송신 정산 상태 / 수취 정산 상태`를 직접 표시
  - [x] settlement detail 응답 확장 시 receiver leg 상태 상세 노출

### FE-5. Trust Center / Secure Storage 설명 정리

- 상태: `1차 반영`
- 해야 할 일:
  - [x] Trust Center fallback 설명에서 `로컬 보호`와 `서버 최종 검증` 책임을 분리
  - [x] TEE, secure storage, local fallback 차이를 사용자 문구와 설정 화면에서 1차 구분
  - [x] native secure storage / web fallback 차이를 상태값으로 1차 노출

### FE-6. 자동/수동 정산 UX

- 상태: `1차 반영`
- 해야 할 일:
  - [x] 정산 설정 시트에서 더미 이력을 제거하고 현재 모드 중심으로 정리
  - [x] 수동 정산은 온라인 전용이라는 제약을 시트에서 직접 노출
  - [x] 수동 정산 버튼이 오프라인일 때 `온라인 필요` 상태로 보이도록 1차 정리
  - [x] 자동 정산 진행 중 상태와 수동 요청 상태 1차 구분
  - [x] 받은쪽 `Settle now` / `Auto settle` 실행 시 완료된 수취 합계를 기존 담보 충전 큐(`COLLATERAL_TOPUP`)로 등록
  - [x] 담보 전환 요청에 사용된 received item id를 로컬 consumed marker로 저장해 동일 수취 내역 중복 전환을 방지
  - [x] 받은쪽 총 수취금액은 담보 전환 요청 후 로컬 표시에서 제외해 `정산하기를 누르면 수취 합계는 리셋` 문구와 일치시킴

### FE-7. 알림 로컬/서버 경계 정리

- 상태: `완료`
- 해야 할 일:
  - [x] 결과 모달, 로컬 로그, 서버 알림 히스토리의 역할 설명 분리
  - [x] 알림 설정 시트 설명을 `즉시 팝업 / 최근 사용자 알림 / 정산 센터` 기준으로 정리
  - [x] 최근 알림 페이지 도움말과 재시도 힌트를 동기화 실패 중심으로 정리
  - [x] 서버 알림 히스토리와 로컬 알림 기록을 실제 데이터 소스 기준으로 더 분리할지 결정
