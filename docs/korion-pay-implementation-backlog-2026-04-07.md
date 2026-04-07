# KORION PAY 구현 백로그

작성일: 2026-04-07

연관 점검 문서:
- [korion-pay-implementation-audit-2026-04-07.md](/Users/an/work/offline_pay/docs/korion-pay-implementation-audit-2026-04-07.md)

## 레포 위치

- `offline_pay`: `/Users/an/work/offline_pay`
- `coin_manage`: `/Users/an/work/coin_manage`
- `foxya_coin_service`: `/Users/an/work/foxya_coin_service`
- `coin_front`: `/Users/an/work/coin_front`

## 협업 원칙

- 이 문서는 `반영 안 됨` 또는 `부분 반영` 항목만 기준으로 작성한다.
- 작업은 가능하면 `repo 단위`가 아니라 `기능 축 단위`로 묶는다.
- 프런트와 서버 상태명은 같은 의미를 쓰되, 최종 판정 권한은 서버가 가진다.
- `QR/NFC/BLE`, `collateral`, `settlement`, `receiver leg`, `trust/TEE`는 동시에 수정될 수 있으므로 contract 변경 시 관련 레포를 같이 갱신한다.

## 우선순위 요약

1. 온라인 결제 시 `총 담보금 + 사용 가능 금액` 동시 차감 모델 정리
2. Wallet-Pay 동기화 강화
3. Receiver 정산 구조 명확화
4. TEE/secure storage 책임 경계 명확화
5. 현장 검증과 서버 최종 판정 UX 분리
6. 온라인 담보 작업 direct path 정리
7. 자동/수동 정산 UX 구분
8. 알림 전달 모델 통일

---

## 백로그

### 1. 온라인 결제 시 총 담보금/사용 가능 금액 동시 차감 모델 정리

- 상태: `부분 반영`
- 우선순위: `높음`
- 관련 레포:
  - `coin_front`: `/Users/an/work/coin_front`
  - `offline_pay`: `/Users/an/work/offline_pay`
  - `coin_manage`: `/Users/an/work/coin_manage`

#### 현재 문제

- 온라인 결제 후 사용자 기대는 `총 담보금`과 `사용 가능 금액`이 같이 줄어드는 것이다.
- 현재 프런트 projection은 send 시 `remainingAmount` 중심으로 먼저 움직인다.
- 최종 정합성은 서버 정산과 snapshot refresh 후에 맞는다.

#### 해야 할 일

- `coin_front`
  - `online settlement success` 시 `lockedAmount`, `remainingAmount`를 동시에 반영하는 projection 정책 정리
  - Asset Hub 문구와 숫자 반영 시점을 online/offline 별로 분리
  - settlement tracking 결과가 `SETTLED`일 때 카드 상단 값 즉시 refresh
- `offline_pay`
  - settlement detail 응답에 `postSettlementLockedAmount`, `postSettlementRemainingAmount` 또는 동등한 snapshot hint 제공 검토
- `coin_manage`
  - finalize 시점 원장 결과를 snapshot API에서 더 빠르게 반영할 수 있는 기준값 제공 검토

#### 완료 기준

- 온라인 결제 직후 사용자는 `총 담보금`과 `사용 가능 금액`이 함께 감소한 상태를 본다.
- 오프라인 결제 직후는 기존대로 `사용 가능 금액`만 먼저 감소한다.

---

### 2. Wallet-Pay 동기화 강화

- 상태: `부분 반영`
- 우선순위: `높음`
- 관련 레포:
  - `coin_front`: `/Users/an/work/coin_front`
  - `offline_pay`: `/Users/an/work/offline_pay`
  - `foxya_coin_service`: `/Users/an/work/foxya_coin_service`

#### 현재 문제

- 현재는 snapshot/polling 기반 정합성 유지에 가깝다.
- 사용자가 느끼는 `Wallet = Pay 항상 동기화`와 약간의 시차가 있다.

#### 해야 할 일

- `coin_front`
  - settlement success / collateral topup / release 직후 강제 snapshot refresh trigger 추가
  - snapshot stale indicator 정리
- `offline_pay`
  - current snapshot endpoint에 refresh hint / version / watermark 추가 검토
- `foxya_coin_service`
  - Wallet history 반영 완료 후 프런트가 stale 여부를 더 쉽게 판단할 수 있는 필드 제공 검토

#### 완료 기준

- 온라인 동작 후 사용자는 수 초 이내에 Wallet과 Pay 수치가 맞춰진다.
- stale 상태면 UI에서 명확히 구분된다.

---

### 3. Receiver 정산 구조 명확화

- 상태: `부분 반영`
- 우선순위: `높음`
- 관련 레포:
  - `offline_pay`: `/Users/an/work/offline_pay`
  - `coin_manage`: `/Users/an/work/coin_manage`
  - `foxya_coin_service`: `/Users/an/work/foxya_coin_service`
  - `coin_front`: `/Users/an/work/coin_front`

#### 현재 문제

- 현재 구조는 `sender proof 중심 + receiver 식별 + history 반영`에 더 가깝다.
- 문서 설명처럼 receiver leg가 독립된 proof 제출/정산/지급 흐름인지가 불명확하다.

#### 해야 할 일

- 구조 결정:
  - `A안`: sender proof 단일 권위 모델 유지
  - `B안`: receiver ack/proof leg 추가
- `offline_pay`
  - receiver leg를 도입할지 결정 후 API/상태 모델 명시
  - settlement detail에 sender/receiver leg 상태 노출
- `coin_manage`
  - receiver 자산 반영이 별도 원장 단계인지, sender finalize의 결과인지 명확화
- `foxya_coin_service`
  - historyType, sender/receiver 표시 모델 정리
- `coin_front`
  - Receiver 정산 상태 UI 분리

#### 완료 기준

- 문서만 봐도 `sender 정산`과 `receiver 정산`의 시스템 책임이 명확하다.
- 서버 상태 조회만으로 sender/receiver 양측 완료 여부를 설명할 수 있다.

---

### 4. TEE / Secure Storage 책임 경계 명확화

- 상태: `부분 반영`
- 우선순위: `높음`
- 관련 레포:
  - `coin_front`: `/Users/an/work/coin_front`
  - `foxya_coin_service`: `/Users/an/work/foxya_coin_service`
  - `offline_pay`: `/Users/an/work/offline_pay`

#### 현재 문제

- trust center와 secure storage는 있으나, 어떤 상태가 반드시 TEE/secure enclave/keystore에 있어야 하는지가 명확하지 않다.
- `initialStateRoot`, spending state, verifier, auth ticket의 저장 책임이 섞여 있다.

#### 해야 할 일

- `coin_front`
  - native 기준 secure storage 대상 목록 정리
  - web fallback과 native secure storage의 정책 차이 문서화
  - spending proof state, pin verifier, pin ticket, device registration metadata 저장소 구분
- `foxya_coin_service`
  - trust center DTO에 단말 보안 수준 설명 필드 보강 검토
- `offline_pay`
  - 서버가 요구하는 최소 trust contract 명시

#### 완료 기준

- 어떤 값이 `TEE/secure storage mandatory`, `local fallback allowed`, `server authority`인지 표로 정리된다.
- 앱 코드와 trust center 설명이 그 표와 일치한다.

---

### 5. 현장 검증과 서버 최종 판정 UX 분리

- 상태: `부분 반영`
- 우선순위: `높음`
- 관련 레포:
  - `coin_front`: `/Users/an/work/coin_front`
  - `offline_pay`: `/Users/an/work/offline_pay`

#### 현재 문제

- 문서상 `서버 없이 검증`과 실제 `서버 최종 판정`이 사용자에게 혼동될 수 있다.
- 성공/보류/실패 문구를 더 명확히 나눌 필요가 있다.

#### 해야 할 일

- `coin_front`
  - 현장 통과 상태를 `결제 가능` 또는 `현장 승인`으로 표현
  - 서버 정산 완료만 `정산 완료` 또는 `Payment Successful`로 표기
  - 보상/리컨실은 `정산 지연`, `처리 중 문제 발생`으로 통일
- `offline_pay`
  - settlement detail reason/status contract를 프런트 용도로 더 안정화

#### 완료 기준

- `현장 승인`과 `최종 정산 완료`가 UI에서 절대 같은 의미로 보이지 않는다.

---

### 6. 온라인 담보 작업 direct path 정리

- 상태: `부분 반영`
- 우선순위: `중간`
- 관련 레포:
  - `coin_front`: `/Users/an/work/coin_front`
  - `offline_pay`: `/Users/an/work/offline_pay`

#### 현재 문제

- 온라인 담보 채우기/해제가 지금은 queue-first 후 immediate sync 구조다.
- 사용자 기대는 online direct action에 가깝다.

#### 해야 할 일

- `coin_front`
  - online 상태에선 direct request path 사용 여부 검토
  - offline일 때만 queue path 사용하도록 분기 정리 검토
- `offline_pay`
  - collateral API 응답만으로 프런트가 optimistic update 가능한지 검토

#### 완료 기준

- 온라인 담보 채우기/해제는 direct path 또는 queue-first path 중 하나로 정책이 명확히 고정된다.

---

### 7. 자동 정산 / 수동 정산 UX 구분

- 상태: `부분 반영`
- 우선순위: `중간`
- 관련 레포:
  - `coin_front`: `/Users/an/work/coin_front`
  - `offline_pay`: `/Users/an/work/offline_pay`
  - `foxya_coin_service`: `/Users/an/work/foxya_coin_service`

#### 현재 문제

- 운영/admin 재처리 기능은 있으나 일반 사용자 수동 정산 UX는 약하다.

#### 해야 할 일

- 자동 정산 기본 정책과 수동 정산 가능 조건 정의
- `coin_front`
  - 수동 정산 가능 시점과 액션 제공 여부 결정
- `offline_pay`
  - retry/manual trigger contract 정리

#### 완료 기준

- 자동/수동 정산 정책이 사용자 화면, 운영 화면, 서버 정책에서 같은 의미를 갖는다.

---

### 8. 알림 전달 모델 통일

- 상태: `부분 반영`
- 우선순위: `중간`
- 관련 레포:
  - `coin_front`: `/Users/an/work/coin_front`
  - `foxya_coin_service`: `/Users/an/work/foxya_coin_service`
  - `offline_pay`: `/Users/an/work/offline_pay`

#### 현재 문제

- 일부는 로컬 로그, 일부는 서버 sync, 일부는 ops alert라 전달 경로가 섞여 있다.

#### 해야 할 일

- 사용자 알림과 운영 알림 분리
- `coin_front`
  - 로컬 저장 후 온라인 동기화할 알림 범위 정의
- `foxya_coin_service`
  - notification history 단일 조회 모델 정리
- `offline_pay`
  - dead-letter, conflict, settlement delay를 사용자 알림으로 승격할지 정책 정리

#### 완료 기준

- `사용자 알림`, `운영 알림`, `로컬 로그`가 서로 다른 책임으로 정리된다.

---

## 추천 구현 순서

1. 온라인 결제 시 `총 담보금/사용 가능 금액` 동시 차감 모델 정리
2. Wallet-Pay 동기화 강화
3. 현장 검증과 서버 최종 판정 UX 분리
4. Receiver 정산 구조 명확화
5. TEE / secure storage 책임 경계 명확화
6. 온라인 담보 작업 direct path 정리
7. 자동/수동 정산 UX 구분
8. 알림 전달 모델 통일

## AI 협업용 메모

- `coin_front` 담당 AI:
  - Asset Hub 수치/문구/상태 UX
  - settlement tracking UI
  - trust center / secure storage UX
- `offline_pay` 담당 AI:
  - settlement state contract
  - receiver leg 구조
  - compensation / reconciliation API 노출
- `coin_manage` 담당 AI:
  - finalize / compensate / receiver side accounting 책임 명확화
- `foxya_coin_service` 담당 AI:
  - Wallet/history/notification/trust center 표시 모델 정리

## 주의

- contract 변경 시 `offline_pay`만 수정하지 말고 반드시 `coin_front`, `coin_manage`, `foxya_coin_service` 영향 범위를 같이 본다.
- `반영 안 됨` 항목을 먼저 줄이고, 표현만 바꾸는 문서 수정은 그 뒤에 한다.
