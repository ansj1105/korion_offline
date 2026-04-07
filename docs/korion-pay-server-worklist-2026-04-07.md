# KORION PAY 서버 작업리스트

작성일: 2026-04-07

레포:
- `offline_pay`: `/Users/an/work/offline_pay`
- `foxya_coin_service`: `/Users/an/work/foxya_coin_service`

연관 문서:
- [korion-pay-implementation-audit-2026-04-07.md](/Users/an/work/offline_pay/docs/korion-pay-implementation-audit-2026-04-07.md)
- [korion-pay-implementation-backlog-2026-04-07.md](/Users/an/work/offline_pay/docs/korion-pay-implementation-backlog-2026-04-07.md)

## 우선순위

1. settlement detail contract 강화
2. Receiver 정산 구조 명확화
3. trust / TEE contract 명확화
4. snapshot refresh hint 제공
5. 자동/수동 정산 정책 명시
6. 알림/히스토리 모델 정리

## 작업 항목

### BE-1. Settlement detail 응답 강화

- 상태: `1차 반영`
- 레포: `offline_pay`
- 해야 할 일:
  - [x] proof / collateral / saga / reconciliation 상태를 한 응답에서 조회 가능하게 확장
  - [x] `coin_manage` ledger sync 결과(`ledgerOutcome`, `duplicated`, `accountingSide`, `receiverSettlementMode`, `post*Balance`)를 settlement detail 응답에 포함
  - [x] sender/receiver leg 상태 분리 — `senderHistoryStatus` / `receiverHistoryStatus` 필드를 settlement detail 응답에 추가 (saga step `HISTORY_SYNCED` / `RECEIVER_HISTORY_SYNCED` 기반)
  - [x] 현장 검증 통과(`proof 유효` → status PENDING) vs 서버 최종 판정(`status SETTLED/REJECTED`) 구분: `status` 필드가 서버 판정 기준, `sagaStep`이 내부 처리 단계 기준 — 응답에 두 값 모두 노출되므로 FE에서 구분 가능

### BE-2. Receiver 정산 구조 확정

- 상태: `1차 반영`
- 레포:
  - `offline_pay`
  - `foxya_coin_service`
- 해야 할 일:
  - [x] sender proof 단일 권위 모델 유지 — receiver는 별도 proof leg 없이 sender proof 기준 처리
  - [x] `RECEIVER_HISTORY_SYNC_REQUESTED` 이벤트 타입 추가 (saga 체이닝)
  - [x] `OFFLINE_PAY_RECEIVE` TransactionType 추가 (foxya_coin_service)
  - [x] `transferRef` 필드 추가: sender = `settlementId`, receiver = `settlementId:R` (unique key 충돌 방지)
  - [x] `createReceiverHistoryCommand()` factory 메서드 추가 (offline_pay)
  - [x] sender/receiver leg 각각의 정산 상태를 settlement detail 응답에 명시적으로 노출 — `senderHistoryStatus` / `receiverHistoryStatus` 추가, `RECEIVER_HISTORY_SYNCED` saga step으로 추적

### BE-3. Trust / TEE 최소 계약 정의

- 상태: `1차 반영`
- 레포:
  - `offline_pay`
  - `foxya_coin_service`
- 해야 할 일:
  - [x] `DeviceTrustContract` 클래스 추가 — 최소 요구 수준: `HARDWARE_BACKED_VERIFIED` (offline_pay)
  - [x] `TRUST_CONTRACT_NOT_MET`, `TRUST_LEVEL_DEGRADED` reason code 추가 (offline_pay)
  - [x] `SettlementPolicyEvaluator` — proof payload의 `deviceTrustLevel` 읽어서 `trustContractMet` resultJson에 기록 (비차단, 관측용)
  - [x] `OfflinePayTrustCenterDto`에 `trustContractMet` (Boolean), `contractRequirements` (String) 필드 추가 (foxya_coin_service)
  - [x] `normalizeOfflinePayTrustCenter`, `defaultOfflinePayTrustCenter`, `getOfflinePayTrustCenter` 빌더에서 `trustContractMet` 계산 적용
  - [x] trust contract 게이트: **비차단 유지 결정** — 현재 단계에서 TEE 미지원 기기가 다수이므로 차단 시 서비스 불가. `trustContractMet=false`는 resultJson에 관측용으로만 기록. 향후 TEE 보급률 임계치 도달 시 WARN → REJECT 단계 전환 예정

### BE-4. Snapshot refresh hint

- 상태: `1차 반영`
- 레포:
  - `offline_pay`
  - `foxya_coin_service`
- 해야 할 일:
  - [x] `CollateralSnapshot`에 `snapshotVersion` (updatedAt epoch ms) 추가 — FE stale 감지용
  - [x] `CurrentSnapshot`에 `staleAfterMs` (60,000ms) 추가 — 서버 freshness hint
  - [x] `OfflinePayTrustCenterDto`에 `snapshotRefreshedAt` (응답 시각) + `staleAfterMs` (300,000ms) 추가
  - [x] `SettlementPolicyConstants`에 stale hint 상수 통합

### BE-5. 정산 정책 명시

- 상태: `1차 반영`
- 레포: `offline_pay`
- 해야 할 일:
  - [x] `SubmitSettlementBatchRequest`에 `triggerMode` 필드 추가 (AUTO / MANUAL, default MANUAL)
  - [x] `SettlementBatchFactory`에서 `triggerMode` summaryJson 기록
  - [x] `SettlementBatchDetailResponse`에 `triggerMode` 노출
  - [x] `SettlementPolicyConstants` 클래스 생성 — trigger mode, reconciliation 조건, ledger mode 상수 + Javadoc
  - [x] 리컨실 운영 가이드: dead-lettered → `GET /settlements/requests/{id}` 확인 → 외부 원장 수동 보정 → `POST /settlements/{id}/finalize` 호출 → saga 재개. `reconciliationStatus=OPEN`이면 ops 개입 필요 signal

### BE-6. 사용자 알림 / 운영 알림 경계

- 상태: `1차 반영`
- 레포:
  - `offline_pay`
  - `foxya_coin_service`
- 해야 할 일:
  - [x] `OfflinePayActivityLogDto`에 `audience` (USER/OPS/BOTH), `logSource` (SETTLEMENT_FLOW 등) 추가
  - [x] settlement center 읽기 시 `audience` / `logSource` 파생 계산 (COMPLETED/FAILED → BOTH, 나머지 → USER)
  - [x] notification center 읽기 시 `notificationType` 기반 `logSource` 자동 분류
  - [x] `NotificationBoundary` 상수 클래스 생성 (offline_pay) — 경계 정의 문서화
  - [x] 운영 전용 알림 사용자 센터 노출 방지 — `normalizeOfflinePayActivityCenter`에서 `audience == "OPS"` 로그 필터링 추가 (foxya_coin_service UserService)
