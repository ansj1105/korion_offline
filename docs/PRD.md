# Offline Collateral-Based Digital Asset Payment System PRD

## 1. Overview

### 1.1 Objective
본 시스템은 완전 무서버 오프라인 송금 구조가 아니라 다음 흐름을 전제로 한다.

- 온라인 자산 일부를 담보로 잠금
- 기기 단위 오프라인 사용권 생성
- 단말 간 proof 전송
- 수신 단말의 오프라인 검증
- 온라인 복귀 후 서버 사후 정산

즉, `담보 잠금 + 오프라인 사용권 생성 + 기기 서명 + 오프라인 검증 + 온라인 사후 정산` 구조를 기반으로 디지털 자산을 오프라인 환경에서 사용할 수 있도록 한다.

### 1.2 Goals
- 오프라인 결제 지원
- 이중 사용 위험 최소화
- 빠른 PoC 구현
- 확장 가능한 구조 설계

### 1.3 Design Principles
- 최종 정산은 반드시 서버에서 수행한다.
- 오프라인 환경에서는 전역 상태 일관성을 보장하지 않는다.
- 완전한 이중지불 차단이 아닌 리스크 제한과 사후 정산 기반으로 설계한다.
- 오프라인 한도는 기기 단위로 분리한다.
- proof 포맷은 strict canonical serialization 기반으로 고정한다.
- 모든 보안 정책은 서버 기준으로 최종 판단한다.

### 1.4 Non-Goals
- 완전 무신뢰 오프라인 전자현금 구현
- 오프라인에서 글로벌 이중지불 완전 차단
- HW monotonic counter 필수 구현
- Secure Element 의존 구조
- 자동 분쟁 해결 시스템

## 2. Scope

### In Scope
- 담보 기반 오프라인 결제
- 기기 단위 오프라인 한도 관리
- 해시 체인 상태 관리
- 단말 간 P2P proof 전송
- 전자서명 기반 proof 생성
- 오프라인 검증: 형식, 서명, 만료, 중복
- 사후 정산 서버
- 기기 및 키 관리
- 관리자 충돌 로그

### Out of Scope
- 하드웨어 보안 모듈 기반 counter
- 완전한 tamper-proof 환경
- 자동 분쟁 해결 시스템

## 3. System Architecture

### 3.1 Server Components
- Collateral Service
- Settlement Service
- Device Registry
- Risk Engine

### 3.2 Client Components
- Secure State Store
- Offline Payment Engine
- Proof Generator / Signer
- Proof Verifier
- Nearby Communication Module (BLE/NFC/QR)

### 3.3 Backend Runtime Choice
- Language/Runtime: Java 17 + Spring Boot
- HTTP Framework: Spring MVC
- Database: PostgreSQL + Flyway
- Event Processing: Redis Streams
- Deployment: EC2 Docker Compose
- External Integration: proxy through existing `coin_manage`

선정 이유:
- 정산 엔진, 상태 전이, 정책 판정, 배치 처리에 강한 타입 안정성이 필요하다.
- 도메인 enum과 서비스 계층이 길어져도 Java가 장기 리팩토링에 더 유리하다.
- Spring은 트랜잭션 경계, 검증, 운영 구조를 잡기 쉽다.
- Redis Streams는 settlement consumer group 처리에 충분하고 BullMQ보다 의존이 가볍다.

## 4. Data Model

### 4.1 `devices`
- `device_id`
- `user_id`
- `public_key`
- `status`
- `metadata`

### 4.2 `device_risk_profiles`
- `device_id`
- `user_id`
- `max_offline_amount`
- `policy_version`
- `status`
- `metadata`

### 4.3 `collateral_locks`
- `user_id`
- `device_id`
- `asset_code`
- `amount`
- `remaining_amount`
- `initial_state_root`
- `policy_version`
- `status`
- `external_lock_id`
- `expires_at`

### 4.4 `offline_payment_proofs`
- `collateral_id`
- `sender_device_id`
- `receiver_device_id`
- `hash_chain_head`
- `previous_hash`
- `signature`
- `amount`
- `payload`

### 4.5 `settlement_requests`
- `collateral_id`
- `proof_id`
- `status`
- `conflict_detected`
- `settlement_result`

### 4.6 `settlement_conflicts`
- `settlement_id`
- `conflict_type`
- `severity`
- `detail`

## 5. Primary Flows

### 5.1 Collateral Creation
1. Client가 온라인 상태에서 담보 잠금을 요청한다.
2. 서버는 등록된 기기와 정책 버전을 확인한다.
3. 서버는 `coin_manage` 프록시를 통해 자산 잠금을 요청한다.
4. 성공 시 `collateral_locks`에 남은 금액과 초기 state root를 기록한다.

### 5.2 Offline Proof Transfer
1. 송신 단말은 담보 범위 내에서 proof를 생성한다.
2. proof는 canonical serialization과 기기 서명을 포함한다.
3. 수신 단말은 오프라인에서 형식, 서명, 만료, 이전 해시 연결성을 검증한다.

### 5.3 Settlement
1. 온라인 복귀 후 proof를 서버에 제출한다.
2. 서버는 proof 저장 후 settlement 요청을 생성한다.
3. worker는 settlement를 processing으로 전환한다.
4. 정산 결과에 따라 담보 release 또는 adjustment를 `coin_manage`에 전달한다.

### 5.4 Conflict Handling
다음 상황은 conflict 대상으로 본다.

- 동일 collateral에서 해시 체인 분기가 감지된 경우
- 이미 사용된 proof 또는 중복 정산이 감지된 경우
- 만료된 사용권 제출
- 기기 정책 한도 초과

conflict는 관리자 로그에 남기고 자동 분쟁 해결은 초기 범위에서 제외한다.

## 6. Initial API

### 6.1 Device
- `POST /api/devices/register`

### 6.2 Collateral
- `POST /api/collateral`

Request example:

```json
{
  "userId": 1,
  "deviceId": "device-abc",
  "amount": "1000",
  "assetCode": "USDT",
  "initialStateRoot": "GENESIS",
  "policyVersion": 1
}
```

### 6.3 Settlement
- `POST /api/settlements`
- `POST /api/settlements/:settlementId/finalize`

## 7. Integration Boundary

기존 `coin_manage`와의 통신은 직접 결합이 아니라 proxy 경계로 둔다.

- collateral lock 요청
- settlement finalize 시 release 또는 adjust 요청
- 이후 단계에서 wallet freeze, policy lookup, admin conflict sync 확장 가능

## 8. Implementation Notes

현재 스캐폴드에 반영된 범위:
- Spring Boot 계층 구조
- Spring MVC HTTP API
- Flyway 초기 스키마
- Redis Streams settlement worker
- `coin_manage` proxy gateway 골격

다음 구현 우선순위:
1. strict canonical proof schema 정의
2. 해시 체인 검증과 double-spend conflict 판정
3. device risk profile CRUD와 정책 적용
4. 관리자 conflict 조회 API
