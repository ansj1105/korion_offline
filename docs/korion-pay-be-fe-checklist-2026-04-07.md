# KORION PAY BE→FE 연동 체크리스트

작성일: 2026-04-07

FE 작업리스트 기준으로 각 항목에 필요한 BE 작업 완료 여부를 정리합니다.

---

## FE-1. Asset Hub 상단 카드 수치/문구 정리 (`진행중`)

| BE 의존 항목 | 상태 |
|---|---|
| `remainingAmount` API 응답 포함 여부 | ✅ BE-1에서 settlement detail에 `collateralRemainingAmount` 포함 |
| `lockedAmount` API 응답 포함 여부 | ✅ BE-1에서 `collateralLockedAmount` 포함 |
| online settlement 후 snapshot 동시 갱신 hint | ⏳ BE-4 미완 (refresh watermark 미제공) |

**FE 진행 가능 범위**: `remainingAmount`/`lockedAmount` 표시는 가능. stale indicator는 BE-4 이후.

---

## FE-2. Snapshot stale / refresh UX (`1차 반영`)

| BE 의존 항목 | 상태 |
|---|---|
| settlement 완료 후 강제 refresh 신호 | ✅ 기존 응답 구조에서 FE가 이미 처리 중 |
| stale watermark / 마지막 갱신 시각 hint | ⏳ BE-4 미완 |

**FE 진행 가능 범위**: refresh 타이밍 로직은 FE 자체로 처리 가능. watermark hint는 BE-4 이후.

---

## FE-3. 현장 검증 vs 최종 정산 상태 문구 분리 (`1차 반영`)

| BE 의존 항목 | 상태 |
|---|---|
| `ledgerOutcome` 응답 포함 | ✅ BE-1에서 settlement detail에 포함 |
| `postAvailableBalance`, `postLockedBalance`, `postOfflinePayPendingBalance` | ✅ BE-1에서 포함 |
| `accountingSide`, `receiverSettlementMode`, `ledgerDuplicated` | ✅ BE-1에서 포함 |
| `현장 승인` / `정산 완료` 상태 라벨 통일을 위한 status/reason 구분 | ⏳ BE-1 미결 항목 (sender/receiver 상태 분리) |

**FE 진행 가능 범위**: ledger 결과 기반 문구 표시 가능. 상태 라벨 완전 통일은 BE-1 잔여 작업 이후.

---

## FE-4. Receiver 정산 상태 UI (`1차 반영`)

| BE 의존 항목 | 상태 |
|---|---|
| receiver 정산 history 기록 (`OFFLINE_PAY_RECEIVE`) | ✅ BE-2에서 `RECEIVER_HISTORY_SYNC_REQUESTED` 및 `OFFLINE_PAY_RECEIVE` 추가 |
| `transferRef` 구분 (sender vs receiver) | ✅ BE-2에서 `settlementId` vs `settlementId:R` 분리 |
| settlement detail에서 receiver leg 상태 명시 노출 | ⏳ BE-2 미결 항목 |

**FE 진행 가능 범위**: receiver history 조회 및 수취 확인 완료 화면 구현 가능. receiver 상세 상태 노출은 BE-2 잔여 작업 이후.

---

## FE-5. Trust Center / Secure Storage 설명 정리 (`1차 반영`)

| BE 의존 항목 | 상태 |
|---|---|
| `trustContractMet` 필드 (foxya trust center DTO) | ✅ BE-3에서 추가 |
| `contractRequirements` 필드 | ✅ BE-3에서 추가 (`"HARDWARE_BACKED_VERIFIED"`) |
| `serverVerifiedTrustLevel` 값 노출 | ✅ 기존 DTO에 존재 |
| settlement result JSON에 `trustContractMet` 기록 | ✅ BE-3에서 `SettlementPolicyEvaluator` 결과 JSON에 포함 |
| trust contract 미충족 시 차단 게이트 | ⏳ 비차단 유지 중 — 정책 결정 후 적용 예정 |

**FE 진행 가능 범위**: `trustContractMet` / `contractRequirements` 기반 Trust Center 화면 설명 구분 가능.

---

## FE-6. 자동/수동 정산 UX (`대기`)

| BE 의존 항목 | 상태 |
|---|---|
| 자동/수동 정산 정책 및 상태값 명확화 | ⏳ BE-5 미완 |

**FE 진행 가능 범위**: BE-5 완료 이후 착수 권장.

---

## FE-7. 알림 로컬/서버 경계 정리 (`대기`)

| BE 의존 항목 | 상태 |
|---|---|
| settlement/history/notification 로그 책임 분리 | ⏳ BE-6 미완 |

**FE 진행 가능 범위**: BE-6 완료 이후 착수 권장.

---

## 요약

| FE 항목 | BE 준비 | FE 착수 가능 여부 |
|---|---|---|
| FE-1 Asset Hub 카드 | BE-1 ✅ / BE-4 ⏳ | 부분 가능 (stale 제외) |
| FE-2 Snapshot refresh | BE-4 ⏳ | 부분 가능 (타이밍 자체 처리) |
| FE-3 검증 vs 정산 상태 | BE-1 ✅ (ledger) / 잔여 ⏳ | 부분 가능 |
| FE-4 Receiver 정산 상태 | BE-2 ✅ / 잔여 ⏳ | 부분 가능 |
| FE-5 Trust Center | BE-3 ✅ | 가능 |
| FE-6 자동/수동 UX | BE-5 ⏳ | 대기 |
| FE-7 알림 경계 | BE-6 ⏳ | 대기 |
