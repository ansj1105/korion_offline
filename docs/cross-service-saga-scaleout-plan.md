# Cross-Service Saga / Scale-Out Plan

## 1. 목적

- `offline_pay -> coin_manage -> foxya -> coin_publish -> coin_csms` 흐름을
  공통 saga 상태 계약으로 묶는다.
- 각 서비스는 자기 DB 트랜잭션만 보장하고, 서비스 간 정합성은
  `outbox + worker + projector + compensation`으로 맞춘다.
- 초기에는 DB outbox 기반으로 운영하고, scale-out 임계점에서만 broker 도입을 검토한다.

## 2. 공통 상태 계약

### 2.1 Saga Status

- `ACCEPTED`
- `PROCESSING`
- `PARTIALLY_APPLIED`
- `COMPLETED`
- `FAILED`
- `DEAD_LETTERED`

### 2.2 Workflow Stage

- `LOCAL_QUEUED`
- `SERVER_ACCEPTED`
- `PROCESSING`
- `LEDGER_LOCKED`
- `COLLATERAL_RELEASED`
- `LEDGER_SYNCED`
- `HISTORY_SYNCED`
- `FAILED`
- `DEAD_LETTERED`

### 2.3 Failure Class

- `TRANSPORT`
- `BUSINESS`
- `SYSTEM`

## 3. 서비스 역할

### 3.1 offline_pay

- 오프라인 담보/정산 orchestration
- source of truth for offline workflow
- outbox event 발행
- workflow projector / saga state 유지

### 3.2 coin_manage

- 원장 source of truth
- 담보 lock/release
- 정산 ledger sync
- outbox summary / workflow projection 제공

### 3.3 foxya

- user-facing wallet/history projection
- offline workflow summary read model 제공
- history sync consumer

### 3.4 coin_publish

- withdrawal / sweep worker
- publish event에 workflow/saga 메타 포함

### 3.5 coin_csms

- 관리자 bridge
- `coin_manage` outbox / workflow summary 집계 노출

## 4. 관리자 모니터링 기준

- `workflow states`
- `sagas`
- `dead-letter`
- `reconciliation`
- `failureClass` 집계
- `workflowStage` 집계

관리자 화면은 개별 이벤트보다 아래 집계를 우선 보여준다.

- `PROCESSING` stuck count
- `FAILED` count
- `DEAD_LETTERED` count
- `BUSINESS / TRANSPORT / SYSTEM` failure count

## 5. Broker 도입 기준

초기 기본값:

- DB outbox + polling worker 유지

Broker 필요 조건:

- 단일 서비스당 outbox publish backlog가 10,000건 이상 지속
- polling latency가 5초 이상 지속
- 동일 이벤트 fan-out consumer가 3개 이상으로 증가
- cross-service near-real-time propagation 요구가 커짐
- replay / partition / ordered consumer group 운영 필요

도입 후보:

- Kafka
- RabbitMQ

현 단계 판단:

- 아직은 broker 강제 단계 아님
- 먼저 모든 서비스에 공통 saga contract / projector / admin monitoring을 맞춘다

## 6. 단계별 실행 순서

1. `offline_pay` workflow/saga projector 안정화
2. `coin_manage` workflow summary + failureClass 정착
3. `foxya` workflow summary / projection 반영
4. `coin_publish` publish worker saga metadata 반영
5. `coin_csms` admin bridge / summary 노출
6. `fox front` 관리자 화면이 존재하면 같은 summary 연결
7. backlog/latency 측정 후 broker 도입 여부 결정

## 7. 운영 체크리스트

- health endpoint 정상
- outbox backlog 추이
- dead-letter 증가 추이
- saga `PROCESSING` stuck 여부
- failureClass 비율
- compensation 실행 여부
- Telegram 경보가 `BUSINESS`와 `SYSTEM`을 혼동하지 않는지 확인
