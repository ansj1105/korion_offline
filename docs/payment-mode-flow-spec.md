# KORION Offline Payment - Payment Mode & Flow Specification

## 1. 목적

이 문서는 KORION 오프라인 결제의 앱 동작 기준과 서버 역할을 함께 정의한다.

핵심 원칙은 아래와 같다.

- 오프라인 페이는 `online/offline` 여부와 무관하게 진입 가능해야 한다.
- 결제의 본질은 `원장 기반 pay`다.
- 오프라인일 때는 요청을 로컬 큐에 적재한다.
- 온라인 복귀 후 서버가 배치 처리로 정합성을 판정하고 내부 원장 반영을 완료한다.
- 자동 실출금은 하지 않는다.

## 2. 결제 모드

### 2.1 Receive

- 상대방에게 결제 요청을 보내는 수신/요청 모드
- 금액 입력 가능
- 빠른결제 진입 가능

### 2.2 Send

- 사용자가 직접 금액을 입력하고 송금하는 모드
- 빠른결제의 핵심 인증 주체

### 2.3 Scan QR

- QR 기반 인증/결제 진입 모드
- QR 스캔 후 실제 전송/확인/완료 흐름은 Send/Receive 공통 흐름을 따른다

## 3. 기본 진입 상태

앱이 오프라인 페이 화면에 처음 진입했을 때, 사용자가 특정 버튼을 누르지 않았더라도 시스템은 기본적으로 `Idle Discoverable` 상태여야 한다.

- UI: `Receive`처럼 보일 수 있음
- 내부 상태: `IDLE_DISCOVERABLE`

이 상태에서는 Send/Receive 어느 쪽 기기와도 탐색 가능해야 한다.

이유:

- 두 사용자가 동시에 기본 상태여도 매칭 실패를 만들면 안 된다.

## 4. 연결 방식

### 4.1 Fast Connection

- NFC 접촉 또는 BLE proximity 기반 빠른 연결
- 금액이 한쪽에서만 정해져 있으면 빠른결제로 진입

### 4.2 Manual Connection

- `Choose from nearby devices`
- BLE 목록에서 수동 선택
- 연결 요청/승인 흐름을 포함할 수 있음

## 5. 결제 유형

### 5.1 Fast Payment

조건:

- 한쪽만 금액 입력 상태
- 기기 접촉 또는 근거리 자동 연결 발생

특징:

- 요청/승인 UI를 최소화
- 즉시 결제 확인 단계로 진입

### 5.2 Manual Payment

조건:

- BLE 목록 수동 선택
- 연결 요청/승인 절차 존재

## 6. 인증 정책

- 항상 `Send`가 인증 주체다.
- `Receiver`는 인증하지 않는다.
- PIN / Biometrics / Face ID 등 로컬 인증은 송금자 기기에서만 수행한다.

## 7. 금액 입력 규칙

- `Send`는 금액 입력 가능
- `Receive`도 금액 입력 가능
- 단, 양쪽 모두 금액을 입력한 채 연결되면 매칭을 즉시 차단해야 한다.

에러 메시지 기준:

`Both devices entered an amount. Only one device can set the amount.`

## 8. 원장 기반 결제 원칙

오프라인 결제는 체인 출금이 아니라 내부 원장 기반 결제다.

- 앱은 `결제 요청 / proof / intent`를 생성한다.
- 서버는 이를 정합성 검증 후 `내부 원장 transaction`으로 반영한다.
- 정산 성공 후 자동 실출금은 수행하지 않는다.
- 실제 체인 출금은 별도 온라인 출금 의도에서 기존 출금 API로만 처리한다.

즉:

- 오프라인 결제 성공 = 내부 원장 이동 성공
- 오프라인 결제 성공 != 외부 네트워크 출금

## 9. 오프라인 큐와 온라인 배치 처리

오프라인 상태에서는 다음을 로컬 큐에 저장한다.

- settlement intent
- collateral topup intent
- collateral release intent

온라인 복귀 시:

1. 앱이 네트워크 전환 이벤트를 감지
2. 로컬 큐를 배치로 업로드
3. `offline_pay`가 proof / intent 정합성을 검증
4. 성공 건만 `coin_manage` 내부 원장에 반영
5. 실패 건은 reason code와 함께 큐에 남김

핵심:

- 온/오프라인과 무관하게 사용자는 오프라인 페이 화면 진입 가능
- 원장 정합성 보장은 서버가 온라인 복귀 후 배치로 수행

## 10. 상태값

```java
enum PaymentMode {
  IDLE,
  SEND,
  RECEIVE
}

enum ConnectionType {
  FAST_CONTACT,
  MANUAL_SELECTION
}

enum PaymentFlow {
  FAST_PAYMENT,
  MANUAL_PAYMENT
}

enum PaymentState {
  IDLE,
  DISCOVERING,
  CONNECTED,
  REQUEST_SENT,
  REQUEST_RECEIVED,
  CONFIRMATION_PENDING,
  AUTH_REQUIRED,
  PROCESSING,
  COMPLETED,
  FAILED
}
```

## 11. 서버 역할

### 11.1 offline_pay

- proof / intent 수신
- 해시 체인 검증
- monotonic counter 검증
- device binding / signature 검증
- collateral 범위 검증
- 배치 단위 판정

### 11.2 coin_manage

- 내부 원장 반영
- proof fingerprint 재검증
- collateral lock / release 반영
- `is_test = 1` 사용자의 mainnet 실출금 금지

### 11.3 fox_coin

- 사용자 거래내역 반영

## 12. 구현상 반드시 유지할 정책

1. 기본 상태는 Receive처럼 보여도 내부적으로는 neutral discoverable 상태여야 한다.
2. Fast / Manual은 UI와 상태값에서 분리되어야 한다.
3. Sender만 인증한다.
4. 양쪽 금액 입력은 연결 시점에 즉시 차단한다.
5. 오프라인 결제는 내부 원장 기반 transaction이다.
6. 오프라인 상태에서는 큐 적재, 온라인 복귀 후 서버 배치 처리로 정합성을 보장한다.
7. 테스트용 peer / 사용자 / 기기 / 금액 데이터는 기본 사용자 화면에 하드코딩하지 않는다.
8. 테스트 데이터가 필요하면 `test mode`로만 노출하고, 실제 기본 동작은 최근 peer / 로컬 캐시 / 실제 서버 스냅샷을 사용한다.
