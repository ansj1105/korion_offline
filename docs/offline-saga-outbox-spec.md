# Offline Saga And Outbox Spec

## 1. 목적

- `offline_pay -> coin_manage -> foxya -> coin_publish` 전 구간을 단일 ACID 트랜잭션으로 묶을 수는 없다.
- 대신 각 서비스는 자기 DB 트랜잭션만 보장하고, 서비스 간 상태는 `outbox + saga + projector + compensation`으로 맞춘다.
- 사용자 화면과 관리자 화면은 같은 `workflowStage`와 `sagaStatus`를 기준으로 읽는다.

## 2. 용어

- `workflowStage`
	- outbox/event 기준 현재 단계
- `sagaStatus`
	- 보상 필요 여부까지 포함한 장기 상태
- `projector`
	- outbox 이벤트를 읽어 `current workflow state`를 계산하는 소비자
- `compensation`
	- 중간 단계 성공 후 후속 단계 실패 시 되돌리기 위한 보상 작업
- `save-point`
	- forward recovery 재시작 또는 backward recovery 되감기의 기준이 되는 단계
- `forward recovery`
	- 실패 시 중지하지 않고 재시도/재처리로 다음 단계를 계속 진행하는 복구
- `backward recovery`
	- 이미 반영된 단계를 보상 트랜잭션으로 되돌리는 복구

## 3. 공통 단계

### 3.1 workflowStage

- `LOCAL_QUEUED`
	- 앱 로컬 큐에만 저장됨
- `SERVER_ACCEPTED`
	- `offline_pay`가 요청을 수락하고 outbox를 적재함
- `SETTLEMENT_ACCEPTED`
	- settlement batch/request가 서버 DB에 저장됨
- `COLLATERAL_LOCKED`
	- `coin_manage` 원장에서 담보 락 성공
- `COLLATERAL_RELEASED`
	- `coin_manage` 원장에서 담보 해제 성공
- `LEDGER_SYNCED`
	- settlement ledger 반영 성공
- `HISTORY_SYNCED`
	- `foxya` history/projection 반영 성공
- `FAILED`
	- 비즈니스 실패 또는 terminal failure
- `DEAD_LETTERED`
	- retry 한도 초과 또는 운영 개입 필요

### 3.2 save-point / recovery mode

- save-point stage
	- `SERVER_ACCEPTED`
	- `SETTLEMENT_ACCEPTED`
	- `COLLATERAL_LOCKED`
	- `COLLATERAL_RELEASED`
	- `LEDGER_SYNCED`
	- `HISTORY_SYNCED`
- forward recovery 우선 stage
	- `LOCAL_QUEUED`
	- `SERVER_ACCEPTED`
	- `SETTLEMENT_ACCEPTED`
- backward recovery 우선 stage
	- `COLLATERAL_LOCKED`
	- `COLLATERAL_RELEASED`
	- `LEDGER_SYNCED`
	- `COMPENSATING`
- terminal stage
	- `FAILED`
	- `DEAD_LETTERED`

### 3.3 sagaStatus

- `QUEUED`
- `ACCEPTED`
- `PROCESSING`
- `PARTIALLY_APPLIED`
- `COMPLETED`
- `COMPENSATION_REQUIRED`
- `COMPENSATING`
- `COMPENSATED`
- `FAILED`
- `DEAD_LETTERED`

### 3.4 saga recovery rule

- `QUEUED`
	- forward recovery
- `ACCEPTED`
	- forward recovery
- `PROCESSING`
	- forward recovery
- `PARTIALLY_APPLIED`
	- forward recovery
- `COMPENSATION_REQUIRED`
	- backward recovery
- `COMPENSATING`
	- backward recovery
- `COMPLETED`
	- no recovery
- `COMPENSATED`
	- no recovery
- `FAILED`
	- terminal
- `DEAD_LETTERED`
	- terminal

## 4. 서비스별 책임

### 4.1 offline_pay

- 오프라인 담보/정산 workflow의 orchestrator
- request 저장과 outbox 적재를 같은 DB 트랜잭션으로 처리
- `workflowStage`, `sagaStatus`, `reconciliation`를 source of truth로 유지

### 4.2 coin_manage

- canonical ledger source of truth
- 담보 락/해제, 정산 반영, 역분개 보상을 책임
- `available`, `offline_pay_pending`, `withdraw_pending` 의미를 고정

### 4.3 foxya

- 사용자 표시용 history/projection consumer
- `coin_manage`가 완료되지 않은 값을 확정값처럼 보여주지 않음

### 4.4 coin_publish

- 외부 알림/발행 consumer
- `COMPLETED` 또는 명시적 발행 조건을 만족한 saga만 외부에 반영

## 5. 사용자 표시 정책

- `총 자산`
	- 마지막 온라인 자산 스냅샷
- `총담보금`
	- 서버가 승인해 잠가둔 담보 총액
- `추가 담보 전환 가능 금액`
	- `총 자산 - 총담보금`
- `오프라인 결제 가능 금액`
	- `총담보금`에서 로컬 pending 사용분까지 반영한 값

## 6. Saga 규칙

### 6.1 담보 충전

1. 앱 로컬 큐 적재
2. `offline_pay` request 저장 + outbox 적재
3. `coin_manage` 담보 락 성공
4. saga `COMPLETED`

실패:

- transport/auth/system
	- `DEAD_LETTERED` 또는 retry
- business
	- `FAILED`

### 6.2 담보 해제

1. 앱 로컬 큐 적재
2. `offline_pay` request 저장 + outbox 적재
3. `coin_manage` 담보 해제 성공
4. saga `COMPLETED`

### 6.3 settlement

1. batch/request 저장
2. ledger sync 성공
3. history sync 성공
4. saga `COMPLETED`

history 단계 실패:

- ledger는 성공했지만 history가 실패했으면
	- saga `COMPENSATION_REQUIRED`
	- 후속 보상 또는 재처리 필요
	- 기본 recovery mode는 `BACKWARD_RECOVERY`
	- save-point는 `LEDGER_SYNCED`

### 6.4 forward recovery

1. `LOCAL_QUEUED -> SERVER_ACCEPTED -> SETTLEMENT_ACCEPTED` 구간 실패는 즉시 rollback보다 재시도 우선
2. transport/auth/system failure는 save-point 이전이면 retry queue로 재진입
3. retry 성공 시 같은 `referenceId` / `Idempotency-Key`로 saga를 이어감

### 6.5 backward recovery

1. `COLLATERAL_LOCKED`, `COLLATERAL_RELEASED`, `LEDGER_SYNCED` 이후 후속 단계 실패 시 보상 필요 여부를 먼저 판단
2. 보상 필요 시 saga는 `COMPENSATION_REQUIRED -> COMPENSATING -> COMPENSATED`
3. 보상 트랜잭션 로그와 원본 saga 상태를 함께 남겨 운영자가 replay-safe 하게 추적

## 7. 관리자 모니터링

- `workflow states`
	- 현재 단계의 단일 view
- `sagas`
	- 장기 상태 및 보상 필요 여부 view
- `outbox`
	- 발행/처리/dead-letter 원본 view
- `reconciliation cases`
	- 예외/부분 성공/충돌 처리 view

## 8. Scale-out 기준

- worker는 무상태(stateless)로 유지
- polling 대상은 `FOR UPDATE SKIP LOCKED` 또는 동등한 reclaim 규칙 사용
- projector는 중복 소비를 허용하고, `upsert`로 최종 상태를 계산
- dead-letter와 retry는 `reasonCode`, `failureClass`, `sagaStatus` 기준으로 집계 가능해야 함
