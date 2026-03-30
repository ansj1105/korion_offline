# Offline Trust Anchor And Approval Contract

## 1. 목적

오프라인 결제는 네트워크 없이도 충분히 강한 사전 검증을 통과해야 한다.  
다만 최종 자산 확정과 원장 기록은 온라인 복귀 후 서버가 수행하는 `최종 승인(final approval)` 결과만을 기준으로 한다.

이 문서는 아래를 고정한다.

- 단말이 로컬에 반드시 보관해야 하는 신뢰 앵커
- 오프라인 payload에 포함되어야 하는 식별/검증 필드
- 온라인 복귀 후 서버가 최종 승인 시 재검증해야 하는 항목
- 서버에 저장해야 하는 최소 결과 모델

## 2. Source Of Truth

- 로컬 큐 원본, 임시 proof, 최근 스냅샷
  - source of truth 아님
  - 오프라인 중 임시 실행을 위한 캐시/대기 저장소
- `offline_pay`
  - 최종 승인 판정기
  - replay 방지, 서명 검증, policy 검증, conflict 검증 담당
- `coin_manage`
  - canonical internal ledger 담당
- `foxya_coin_service`
  - 사용자 인증, 이메일/PIN 상태, 앱 표시용 보안 상태 담당

즉 서버는 “오프라인 큐 전체 저장소”가 아니라 “최종 승인/실패 판정과 감사 기록 저장소”다.

## 3. 로컬 신뢰 앵커

오프라인 결제 단말은 아래 값을 안전 저장소에 보관해야 한다.

### 3.1 민감 저장값

- `registeredDeviceId`
- `devicePrivateKey`
- `devicePublicKey`
- `keyVersion`
- `signedUserId`
- `subjectBindingKey`
- `deviceBindingKey`
- `lastVerifiedAuthMethod`
- `authVerifiedAt`
- `authVerifiedUntil` 또는 동등한 만료 개념
- `pinFailedAttempts`
- `pinLocked`
- `pinLockedAt`

### 3.2 큐/동기화 저장값

- `requestId`
- `nonce`
- `payloadHash`
- `signature`
- `assetCode`
- `network`
- `amount`
- `counterparty`
- `counterpartyUserId` 가능하면 포함
- `queueStatus`
- `syncAttemptCount`
- `syncLastErrorCode`
- `createdAt`
- `expiresAt`

### 3.3 저장 위치 원칙

- iOS
  - Keychain / Secure Enclave
- Android
  - Keystore + encrypted storage
- 큐 / 스냅샷
  - encrypted local DB 권장
- 일반 UI 캐시
  - localStorage 가능
  - 단, 신뢰 판단에는 사용 금지

## 4. 바인딩 키 규칙

### 4.1 subjectBindingKey

사용자 바인딩 키는 같은 사용자인지 판별하는 최소 식별자다.

권장 생성식:

`H(namespace | signedUserId | assetScope | accountVersion)`

현재 구현상 `IssuedProofApplicationService.buildSubjectBindingKey(...)`는
`userId + assetCode` 기반 SHA-256을 사용한다.

### 4.2 deviceBindingKey

기기 바인딩 키는 등록된 단말과 현재 서명 컨텍스트가 같은지 판별한다.

권장 생성식:

`H(namespace | registeredDeviceId | devicePublicKey | keyVersion)`

### 4.3 authBindingKey

최근 인증 세션과 단말 바인딩을 연결한다.

권장 생성식:

`H(namespace | signedUserId | registeredDeviceId | authMethod | authVerifiedAtBucket)`

현재 프런트는 간단한 `authBindingKey`를 저장하지만, 서버 신뢰 기준은
`subjectBindingKey + deviceBindingKey + signature` 조합이어야 한다.

## 5. 오프라인 payload 필수 필드

최종 승인 대상 payload는 최소 아래 필드를 가져야 한다.

- `requestId`
- `nonce`
- `signedUserId`
- `registeredDeviceId`
- `deviceBindingKey`
- `subjectBindingKey`
- `assetCode`
- `network`
- `amount`
- `counterparty`
- `counterpartyUserId` 가능하면 포함
- `issuedAt`
- `expiresAt`
- `monotonicCounter`
- `prevStateHash`
- `newStateHash`
- `authMethod`
- `keyVersion`
- `payloadHash`
- `signature`

옵션이지만 권장:

- `proofId`
- `deviceMetadataVersion`
- `localAvailableAmount`
- `policyVersion`
- `sessionId`

## 6. 오프라인 선검증 규칙

단말은 아래 검증을 통과하지 못하면 큐에 `승인 대기` 상태로 올리지 않는다.

### 6.1 형식 검증

- 필수 필드 존재
- 숫자/소수점 형식
- 시간 형식
- payload canonicalization 가능 여부

### 6.2 정책 검증

- `assetCode` 허용 여부
- `network` 허용 여부
- `expiresAt` 만료 여부
- `amount > 0`
- 자기 자신 대상 전송 금지
- 차단 대상 여부

### 6.3 로컬 정합성 검증

- 마지막 동기화 기준 `available/collateral` 초과 금지
- `monotonicCounter` 증가 보장
- `nonce/requestId` 로컬 중복 금지
- 기기 등록 상태 존재 여부
- 현재 로그인 사용자와 큐 사용자 일치 여부

### 6.4 보안 검증

- `PIN/FINGERPRINT/FACE_ID` 인증 성공
- `pinLocked == false`
- `devicePrivateKey` 접근 가능
- `payloadHash` 재계산 일치
- 단말 키 서명 성공

## 7. 온라인 복귀 후 서버 최종 승인

서버는 성공 승인건만 원장/거래 결과를 남길 수 있다.  
하지만 승인 직전 아래 재검증은 반드시 수행해야 한다.

### 7.1 사용자/기기 바인딩

- `signedUserId`가 현재 등록 단말의 소유자와 일치하는지
- `registeredDeviceId + keyVersion`가 ACTIVE 인지
- `deviceBindingKey`가 등록 정보와 일치하는지
- `subjectBindingKey`가 `signedUserId + assetScope`와 일치하는지

### 7.2 payload 재검증

- `payloadHash` 재계산 일치
- `signature` 검증
- `expiresAt` 미만인지
- `monotonicCounter` 충돌 없는지
- `nonce` replay 없는지
- `requestId` replay 없는지
- `network`, `assetCode`, `counterparty` 정책 일치

### 7.3 원장/정산 검증

- server available amount 초과 여부
- collateral status 유효 여부
- duplicate settlement 여부
- already processed request 여부
- conflict 여부

## 8. 서버 저장 원칙

서버는 로컬 큐 전체를 장기 보관하지 않는다.

저장 대상:

- `requestId`
- `nonce`
- `signedUserId`
- `registeredDeviceId`
- `subjectBindingKey`
- `deviceBindingKey`
- `payloadHash`
- `approvalStatus`
- `reasonCode`
- `approvedAt`
- `failedAt`
- `ledgerReference`
- `historyReference`

즉 서버는 아래 2가지만 남긴다.

- `SUCCESS`
  - 거래/원장/이력 기록
- `FAILED`
  - 실패 사유 코드와 감사용 최소 필드

## 9. 실패 사유 코드 표준

- `DEVICE_NOT_REGISTERED`
- `DEVICE_NOT_ACTIVE`
- `DEVICE_BINDING_MISMATCH`
- `SUBJECT_BINDING_MISMATCH`
- `SIGNATURE_INVALID`
- `PAYLOAD_HASH_INVALID`
- `REQUEST_REPLAYED`
- `NONCE_REPLAYED`
- `COUNTER_CONFLICT`
- `PAYLOAD_EXPIRED`
- `NETWORK_MISMATCH`
- `ASSET_MISMATCH`
- `INVALID_COUNTERPARTY`
- `INSUFFICIENT_LOCAL_AVAILABLE`
- `INSUFFICIENT_SERVER_AVAILABLE`
- `PIN_LOCKED`
- `AUTH_REQUIRED`
- `POLICY_VIOLATION`

## 10. 권장 API 계약

### 10.1 foxya

- `GET /api/v1/security/offline-pay/status`
  - 이메일 인증/PIN 잠금/거래 비밀번호 등록 상태
- `POST /api/v1/security/offline-pay/pin/verify`
  - PIN 실패 횟수, 잠금 상태 갱신

### 10.2 offline_pay

- `POST /api/devices/register`
  - 등록 단말 + 공개키 등록
- `POST /api/devices/revoke`
  - 단말 또는 keyVersion 폐기
- `POST /api/settlements`
  - 오프라인 큐 업로드
  - 수락 시 `PENDING_REVIEW`
- `POST /api/settlements/reverify`
  - 온라인 복귀 후 최종 재검증/승인 전용 경로
  - 결과는 `SUCCESS | FAILED`

## 11. 구현 영향 범위

### 11.1 coin_front

- secure storage 모델 정리
- payload canonicalization
- QR/NFC payload validator 강화
- auth fallback 흐름 유지
- queue 상태를 `PROVISIONAL -> APPROVAL_PENDING -> SUCCESS/FAILED`로 정리

### 11.2 foxya_coin_service

- 오프라인 보안 상태 API 유지
- PIN lock / unlock / 이메일 복구 흐름 유지
- 공유/상세 표시용 최소 public 상태 분리 필요 시 확장

### 11.3 offline_pay

- `deviceBindingKey` 검증 추가
- `requestId` replay 검증 추가
- 성공/실패만 결과 기록하는 승인 경로 정리
- failure reason code 표준화

### 11.4 coin_system_flyway

- 필요 시 replay / approval 감사 테이블 migration
- PIN lock 관련 컬럼은 이미 반영됨

## 12. 결정 메모

- 오프라인에서도 충분한 사전 검증은 필요하다.
- 그렇더라도 서버 최종 재검증은 생략하지 않는다.
- 서버는 “큐 원본 저장소”가 아니라 “최종 승인 결과 기록기”다.
- 신뢰 기준은 `signedUserId` 단독이 아니라
  - `subjectBindingKey`
  - `deviceBindingKey`
  - `registeredDeviceId`
  - `signature`
  조합이다.
