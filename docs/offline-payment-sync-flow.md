# Offline Payment Sync Flow

관련 앱 모드/연결 정책은 [payment-mode-flow-spec.md](./payment-mode-flow-spec.md)를 기준으로 한다.

## 1. 목적

오프라인 결제는 단말이 네트워크 없이도 `전송 요청`을 생성할 수 있어야 한다.  
단, 자산 확정은 오프라인 시점이 아니라 서버 정합성 판정과 내부 원장 반영이 끝난 이후에만 인정한다.

즉, 모바일 단말은 오프라인에서 `요청(intent/proof)`를 만들고 보관한다.
온라인 복귀 후 `offline_pay`가 정합성을 검증하고, 성공 시 worker가 내부 원장 반영을 수행한다.

중요:

- 오프라인 페이는 `원장 기반 pay`다.
- 정산 성공 후 자동 실출금은 수행하지 않는다.
- 외부 체인 출금은 별도의 온라인 출금 의도에서 기존 출금 API만 사용한다.

## 2. 전체 흐름

1. 송신 단말은 오프라인 상태에서 NFC 또는 QR로 상대 단말/가맹점을 식별한다.
2. 송신 단말은 로컬 저장값을 기준으로 `network`, `networkMode`, `token`, `amount`, `description`, `authSessionId`, `prevStateHash`, `newStateHash`, `monotonicCounter`, `nonce`를 묶어 전송 요청을 만든다.
   - 이때 payload에는 `deviceRegistrationId`, `signedUserId`, `authMethod`도 함께 포함된다.
   - native secure-state 경로는 이 바인딩 컨텍스트까지 포함한 payload를 서명한다.
3. 단말은 현재 캐시된 가능 수량이 아니라, 마지막 온라인 동기화 시 받아둔 `offline collateral / available amount` 범위 안에서만 1차 유효성 검사를 수행한다.
4. 전송 직전 로컬 인증 방식을 통과한다.
   - `NONE`
   - `PIN`
   - `FINGERPRINT`
   - `FACE_ID`
5. 단말은 전송 요청을 로컬 큐에 저장한다.
   - 상태 예시: `LOCAL_ONLY`, `SYNC_PENDING`, `SYNCING`, `SYNC_FAILED`, `SYNCED_LEDGER`
6. 온라인 복귀 시 큐 업로드 트리거가 동작한다.
   - `online` 이벤트
   - 앱 foreground 복귀
   - 화면 focus 복귀
   - 수동 재시도
7. `offline_pay`는 큐에 저장된 proof batch를 수신하고 정합성을 검증한다.
8. 검증 성공 시 worker가 실제 후속 트랜잭션을 수행한다.
   - `coin_manage` 내부 원장 반영
   - `fox_coin` 거래내역 기록
9. 검증 실패 시 서버는 실패 사유를 reason code로 반환한다.
10. 단말은 해당 큐 요청을 성공 또는 실패 상태로 갱신한다.

## 3. 서버 판정 규칙

`offline_pay`는 다음 순서로 proof를 평가한다.

1. schema 검증
2. sender device 조회
3. device signature 검증 가능 여부 판정
4. duplicate settlement 검증
5. conflict 검증
6. hash chain 검증
7. settlement policy 검증

### 3.1 해시 체인 규칙

현재 proof의 핵심 상태 전이는 아래 수식으로 검증한다.

`newStateHash = SHA-256(prevStateHash | amount | monotonicCounter | deviceId | nonce)`

이 규칙 때문에:

- 이전 상태 해시가 없으면 다음 상태를 만들 수 없다.
- monotonic counter가 증가하지 않으면 체인이 이어지지 않는다.
- payload를 변조하면 `INVALID_STATE_HASH`가 난다.

### 3.2 단말 서명 규칙

현재 앱은 두 경로를 지원한다.

- `web fallback`
  - localStorage 기반 PoC 서명
  - 서버에서는 legacy/unsupported 경계로 취급
- `native secure state`
  - Android Keystore / iOS Security framework 기반
  - `newStateHash|timestamp|deviceRegistrationId|signedUserId|authMethod`를 ECDSA로 서명
  - 서버는 검증 가능한 공개키가 등록된 경우 서명을 검증하고, 불일치 시 `INVALID_DEVICE_SIGNATURE`로 reject 한다

즉 지금 단계에서 서버는:

- 검증 가능한 서명은 반드시 검증
- 기존 PoC fallback은 아직 완전 차단하지 않음

### 3.3 Spending Proof 필드

현재 전송 proof는 최소 다음 필드를 포함해야 한다.

- `prevStateHash`
- `newStateHash`
- `amount`
- `monotonicCounter`
- `nonce`
- `deviceId`
- `timestamp`
- `signature`
- `deviceRegistrationId`
- `signedUserId`
- `authMethod`

서버는 binding payload가 존재하면 추가로 아래를 확인한다.

- `deviceRegistrationId == registered device row id`
- `signedUserId == device.userId`
- `authMethod` 존재 여부

불일치 시 `INVALID_DEVICE_BINDING`

현재 정책 검증 기준:

- `DEVICE_NOT_ACTIVE`
- `KEY_VERSION_MISMATCH`
- `PROOF_EXPIRED`
- `NETWORK_REQUIRED`
- `NETWORK_NOT_ALLOWED`
- `TOKEN_REQUIRED`
- `TOKEN_MISMATCH`
- `LOCAL_AVAILABLE_AMOUNT_REQUIRED`
- `LOCAL_AVAILABLE_AMOUNT_EXCEEDED`
- `SERVER_AVAILABLE_AMOUNT_EXCEEDED`
- `INVALID_DEVICE_SIGNATURE`

## 4. 성공 이후 책임

중요:

- `오프라인 전송 요청 생성`은 자산 확정이 아니다.
- `서버 정합성 판정 성공`도 아직 최종 사용자 체감 완료와 동일하지 않을 수 있다.
- 최종 확정은 worker가 후속 트랜잭션을 수행하고 원장/거래내역 반영까지 끝난 이후다.

책임 분리는 아래와 같다.

- `coin_front`
  - 오프라인 인증
  - 로컬 큐 저장
  - 온라인 복귀 업로드
  - 큐 상태 표시
- `offline_pay`
  - proof batch 수신
  - 정합성 검증
  - 성공/실패 판정
  - worker 실행 트리거
- `coin_manage`
  - canonical internal ledger 반영
  - collateral lock / release 반영
  - `coin_system_cloud.users.is_test = 1` 사용자는 mainnet 실출금 금지
- `fox_coin`
  - 사용자 거래내역 반영

## 5. 로컬 큐 트리거 규칙

자동 업로드는 단일 조건으로 판단하지 않는다.

- `navigator.onLine === true`
- `online` 이벤트 수신
- `window focus`
- `document.visibilityState === visible`
- 사용자 수동 재시도

이유:

- 모바일 환경에서는 네트워크 복귀와 foreground 복귀가 항상 동시에 오지 않는다.
- `navigator.onLine`만으로는 실제 업로드 가능 시점을 놓칠 수 있다.

## 6. 관리자 모니터링 확장 방향

관리자 대시보드는 최소 다음 축으로 분리되어야 한다.

- `network`
  - `mainnet`
  - `testnet`
- `token`
- `status`
  - requested
  - validating
  - settled
  - failed
  - conflict
  - dead-letter

현재 구현상 관리자 API는 `networkScope=mainnet|testnet` 필터를 지원한다.

- `GET /api/admin/metrics/settlements/timeseries`
- `GET /api/admin/metrics/offline-pay/overview`
- `GET /api/admin/conflicts`
- `GET /api/admin/ops/dead-letters`

필터 기준은 proof `raw_payload.networkMode`이고, 과거 payload는 `network=mainnet`이면 `mainnet`, 그 외는 `testnet`으로 fallback 한다.

## 7. 프런트 현재 구현 상태

현재 `coin_front`는 다음을 수행한다.

- 앱이 오프라인으로 시작되면 근거리 전송 홈으로 fallback
- NFC / QR 오프라인 인증
- 네트워크/토큰/금액/설명 입력
- offline collateral / available amount 기반 1차 검증
- 오프라인 결제 인증 방식 설정
- 등록된 secure device row id를 로컬에 캐시하고 proof에 포함
- 전송 요청 로컬 큐 저장
- 온라인 복귀 시 자동 sync 시도
- 거래내역 화면 내 오프라인 큐 섹션 노출
- Android Keystore / iOS Security framework 기반 secure-state 플러그인 경로 준비

## 8. 다음 구현 우선순위

1. `offline_pay`에서 실패 reason code를 프런트 큐와 완전히 표준화
2. 성공 판정 후 worker가 `coin_manage` 출금/입금 모델과 연결되도록 확장
3. proof payload를 typed contract로 승격
4. settlement 결과와 외부 원장 sync 상태를 별도 outbox/state로 승격
