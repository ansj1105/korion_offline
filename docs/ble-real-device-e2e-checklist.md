# BLE 실기기 2대 E2E 검증 시나리오

## 1. 목적

이 문서는 `BLE request / approve / reject / complete` 흐름을 실제 단말 2대로 검증하기 위한 운영용 시나리오를 정리한다.

검증 목표는 다음 3가지다.

- 주변 기기 탐색과 세션 연결이 실제 단말에서 안정적으로 동작하는지 확인한다.
- `request -> approve/reject -> sender auth -> complete/fail` 상태 전이가 앱/서버 로그 기준으로 일치하는지 확인한다.
- 온라인/오프라인 복구, 큐 저장, 서버 정산, 관리자 추적까지 end-to-end로 이어지는지 확인한다.

## 2. 검증 범위

### 2.1 포함 범위

- Android <-> Android BLE 결제
- Android <-> iOS BLE 결제
- request 생성
- receiver approve
- receiver reject
- sender biometric/PIN auth
- local queue 저장
- 온라인 복구 후 batch sync
- `offline_pay` 정합성 검증
- `coin_manage` 원장 반영
- `foxya` 거래내역 반영
- 관리자 조회 API 반영

### 2.2 제외 범위

- NFC 결제
- QR 결제
- BLE background passive discoverability 장시간 soak test
- iOS NFC 제약 관련 검증

## 3. 사전 조건

### 3.1 단말 조건

- 송신자 단말 1대
- 수신자 단말 1대
- 최소 1대는 Android
- BLE 권한 허용
- 위치 권한이 필요한 Android 버전이면 허용
- 최신 앱 빌드 설치

### 3.2 계정 조건

- 서로 다른 사용자 계정 2개
- 양쪽 모두 오프라인 페이 최초 등록 완료
- 디바이스 등록 완료
- PIN 또는 biometric 등록 완료
- 송신자 계정에 담보금 존재
- 서버 issued proof snapshot 수신 완료

### 3.3 서버 조건

- `offline_pay` health 정상
- `coin_manage` health 정상
- `foxya_coin_service` health 정상
- Telegram alert 설정 정상

## 4. 관찰 포인트

### 4.1 앱 화면 기준

- `Nearby Devices`
- `Nearby Profile`
- `Confirm Payment`
- `Waiting for approval`
- `Final Authorization`
- `Processing`
- `Payment Successful`
- `Settlement Failed`
- `Nearby Connection Failed`

### 4.2 앱 로컬 기준

- local queue item 생성 여부
- event log 생성 여부
- snapshot cache 최신 여부
- duplicate receive cache 반영 여부

### 4.3 서버 기준

- `offline_pay` batch 생성
- proof lifecycle 상태 전이
- settlement request/result 저장
- reconciliation case 저장
- failure reason code 저장
- `coin_manage` ledger finalize
- `foxya` history sync

## 5. 공통 검증 데이터

- asset: `USDT` 또는 현재 운영 asset
- amount:
	- 정상 케이스: `10`
	- 경계 케이스: `availableAmount` 직전 금액
	- 실패 케이스: `availableAmount` 초과 금액
- memo: `ble-e2e-case-001`
- channelType: `BLE`

## 6. OS 조합별 시나리오

### 6.1 iOS -> iOS

#### 목적

iOS 송신자와 iOS 수신자 조합에서 BLE request/approve/auth/sync가 끝까지 닫히는지 검증한다.

#### 주요 확인 포인트

- iOS BLE 권한 팝업 이후 nearby discoverability 유지 여부
- sender auth에서 Face ID 또는 PIN fallback 동작
- queue 저장 후 online 복귀 시 sync 동작
- iOS 특성상 background 복귀 후 snapshot 재동기화 여부

#### 필수 실행 케이스

1. 정상 완료
2. receiver reject
3. sender auth fail
4. 연결 끊김
5. 오프라인 queue 저장 후 온라인 복구

### 6.2 iOS -> Android

#### 목적

크로스 플랫폼 BLE payload 호환성과 상태 전이 일치성을 검증한다.

#### 주요 확인 포인트

- peer profile 식별값과 device model 교환
- request/approve/reject/complete payload 호환성
- issuer proof / sender signature 검증 결과 일치
- 양단 화면 상태가 서로 다르게 보이지 않는지 확인

#### 필수 실행 케이스

1. 정상 완료
2. receiver reject
3. sender auth fail
4. 연결 끊김
5. 오프라인 queue 저장 후 온라인 복구
6. duplicate send

### 6.3 Android -> Android

#### 목적

가장 제약이 적은 조합에서 BLE 전체 시퀀스와 장애 복구까지 기준 케이스를 검증한다.

#### 주요 확인 포인트

- 스캔/선택/연결/전송 안정성
- sender biometric 또는 PIN 인증
- proof lifecycle과 batch sync 일치
- partial success / duplicate send / reconciliation 추적

#### 필수 실행 케이스

1. 정상 완료
2. receiver reject
3. sender auth fail
4. 연결 끊김
5. 오프라인 queue 저장 후 온라인 복구
6. duplicate send
7. partial success

## 7. 공통 시나리오 상세

### 7.1 시나리오 A: 정상 결제 완료

#### 목적

BLE 연결부터 sender auth, queue 저장, 온라인 복구 후 settlement 완료까지 가장 기본 흐름을 검증한다.

#### 절차

1. 송신자 단말에서 `Nearby Send` 진입
2. 수신자 단말도 BLE 대기 화면 진입
3. 송신자 단말에서 수신자 선택
4. 프로필 화면에서 `Send`
5. 금액 입력 후 `Request`
6. 수신자 단말에 요청 수신 확인
7. 수신자 단말에서 `Approve`
8. 송신자 단말에서 `Final Authorization`
9. biometric 또는 PIN 인증 성공
10. 양쪽 단말에서 성공 화면 확인
11. 네트워크가 꺼져 있으면 queue 저장 확인
12. 네트워크 복구 후 batch sync 확인
13. 관리자 API에서 proof/settlement/event 확인

#### 기대 결과

- 송신자:
	- `request_sent -> auth_required -> processing -> success`
- 수신자:
	- `request_received -> waiting -> success`
- local queue:
	- 오프라인이면 생성됨
	- 온라인이면 즉시 서버 전송
- proof lifecycle:
	- `ISSUED -> VERIFIED_OFFLINE -> CONSUMED_PENDING_SETTLEMENT -> SETTLED`
- settlement result:
	- `SETTLED`
- reasonCode:
	- 성공이면 `SETTLED`

### 7.2 시나리오 B: 수신자 거절

#### 목적

receiver reject 시 sender UI와 서버 이벤트가 정확히 분기되는지 검증한다.

#### 절차

1. 송신자 request 생성
2. 수신자 요청 수신
3. 수신자 `Reject`

#### 기대 결과

- 송신자:
	- 거절 모달 표시
- 수신자:
	- 요청 종료
- queue:
	- 완료 proof로 저장되지 않음
- event log:
	- `RECEIVE_REJECTED`
- reconciliation:
	- 필요 시 open case 없이 종료 가능
- reasonCode:
	- `RECEIVE_REJECTED`

### 7.3 시나리오 C: sender auth 실패

#### 목적

sender biometric/PIN 실패 시 거래가 확정되지 않고 실패 이벤트가 저장되는지 검증한다.

#### 절차

1. 송신자 request 생성
2. 수신자 approve
3. 송신자 biometric 실패 또는 PIN 오입력

#### 기대 결과

- 송신자:
	- auth failure 화면 또는 재시도 화면
- 수신자:
	- 완료 화면으로 가지 않음
- event log:
	- `AUTH_BIOMETRIC_FAIL` 또는 `AUTH_PIN_FAIL`
- queue:
	- 결제 proof 확정 저장 안 됨
- reasonCode:
	- auth failure code 저장

### 7.4 시나리오 D: 세션 중 BLE 연결 끊김

#### 목적

request 이후 또는 approve 이전 연결 끊김 시 timeout/interrupted 처리와 재시도 UX를 검증한다.

#### 절차

1. 송신자 request 생성 직전 또는 직후 BLE off
2. 또는 두 단말 거리 이탈

#### 기대 결과

- 화면:
	- `Nearby Connection Failed` 또는 retry 유도
- event log:
	- `BLE_SCAN_FAIL`, `BLE_PAIR_FAIL`, `SEND_INTERRUPTED`, `SEND_TIMEOUT` 중 해당 코드
- queue:
	- 미완료 transaction은 완료 proof로 저장되지 않음
- reasonCode:
	- 연결 단계 실패 code 저장

### 7.5 시나리오 E: 오프라인 queue 저장 후 온라인 복구

#### 목적

오프라인 상태에서 성공한 거래가 온라인 복구 후 서버에 업로드되고 최종 정산되는지 검증한다.

#### 절차

1. 두 단말 모두 오프라인 상태
2. BLE request/approve/auth 완료
3. 양쪽 로컬 queue 확인
4. 송신자 단말 온라인 전환
5. sync 자동 실행 또는 수동 sync

#### 기대 결과

- queue item:
	- 업로드 전까지 `pending`
	- 성공 시 `synced`
- server:
	- batch 생성
	- settlement 처리
- 관리자:
	- offline events
	- batches
	- proofs
	- anomalies
	  에서 추적 가능

### 7.6 시나리오 F: duplicate send

#### 목적

동일 transaction/nonce/counter 재전송 시 중복 전송이 reconciliation case로 남는지 검증한다.

#### 절차

1. 동일 payload를 재업로드하거나 재시도 트리거
2. 서버에 동일 voucher/nonce/counter 기준 중복 유입

#### 기대 결과

- settlement:
	- duplicate reject 또는 conflict
- reconciliation:
	- `DUPLICATE_SEND`
- reasonCode:
	- `DUPLICATE_NONCE` 또는 `DUPLICATE_COUNTER`

### 7.7 시나리오 G: partial success

#### 목적

`offline_pay`는 settlement 성공했지만 외부 원장 또는 history sync가 실패하는 경우를 검증한다.

#### 절차

1. 정상 settlement 직전 `coin_manage` 또는 `foxya` 장애 유발
2. batch finalize 실행

#### 기대 결과

- reconciliation case:
	- `LEDGER_SYNC_FAILED`
	- 또는 `PARTIAL_SETTLEMENT`
- reasonCode:
	- `LEDGER_SYNC_FAIL`
	- 또는 `HISTORY_SYNC_FAIL`
- Telegram:
	- circuit open alert

## 8. 서버 조회 체크리스트

### 8.1 proof 조회

- `/api/admin/proofs`
- `/api/admin/proofs/overview`

확인 항목:

- proof lifecycle status
- reasonCode
- deviceId
- voucherId
- assetCode

### 8.2 sync 조회

- `/api/admin/sync/offline-events`
- `/api/admin/sync/offline-events/overview`
- `/api/admin/batches`
- `/api/admin/batches/overview`
- `/api/admin/batches/dead-letters`

확인 항목:

- eventType
- eventStatus
- channelType
- reasonCode
- batch status

### 8.3 anomaly 조회

- `/api/admin/anomalies/conflicts`
- `/api/admin/anomalies/reconciliation-cases`
- `/api/admin/devices`
- `/api/admin/devices/overview`

확인 항목:

- duplicate send
- partial settlement
- ledger sync fail
- history sync fail
- device status

## 9. 로그 수집 기준

### 9.1 앱 로그

- request 생성 시각
- receiver approve/reject 시각
- sender auth 결과
- queue save 결과
- sync trigger 시각
- sync 결과 및 reasonCode

### 9.2 서버 로그

- proof verify 실패 지점
- issued proof signature verify 실패 지점
- settlement conflict 감지 지점
- external sync failure 지점
- circuit open / recovered 로그

## 10. 실패 판정 기준

다음 중 하나라도 어긋나면 실패로 본다.

- 앱 화면 상태 전이가 양단에서 다르게 보임
- sender auth 성공 전 완료 처리됨
- receiver reject인데 sender가 성공으로 보임
- queue가 생성됐는데 sync 시 batch가 안 생김
- settlement가 실패했는데 reasonCode가 비어 있음
- duplicate send인데 reconciliation case가 없음
- partial success인데 anomaly가 남지 않음

## 11. 실기기 검증 결과 기록 템플릿

### 11.1 실행 환경

- sender device:
- receiver device:
- sender os/app version:
- receiver os/app version:
- channel:
- network state:

### 11.2 실행 결과

- scenario id:
- pass/fail:
- settlement id:
- batch id:
- proof id:
- reconciliation case id:
- reasonCode:

### 11.3 비고

- 재현 여부:
- 스크린샷:
- 서버 로그 위치:
- 앱 로그 위치:
