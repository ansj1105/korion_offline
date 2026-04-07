# KORION PAY 구현 반영 점검

작성일: 2026-04-07

기준 레포:
- `coin_front`
- `offline_pay`
- `coin_manage`
- `foxya_coin_service`

상태 기준:
- `반영`: 요구 구조가 코드상 명확히 구현됨
- `부분 반영`: 방향은 맞지만 구현 방식이나 책임 분리가 설명과 1:1은 아님
- `미반영`: 현재 코드상 핵심 구조를 확인하지 못함

## 1. 전체 구조 점검

| 섹션 | 상태 | 판단 |
| --- | --- | --- |
| 담보 기반 + 오프라인 사용권 + 사후 정산 | 반영 | 담보 생성, 오프라인 proof 생성, 온라인 복귀 후 정산/보상 흐름이 전 레포에 연결됨 |
| 담보 잠금 + 해시 체인 상태 | 반영 | 담보 잠금과 proof hash-chain 검증이 모두 구현됨 |
| 단방향 카운터 기반 오프라인 검증 | 반영 | monotonic counter 생성/검증, gap/replay/conflict 검출 구현 |
| 서버 없이 검증 | 부분 반영 | 현장 1차 검증은 가능하지만 최종 권위 판정은 서버 정산에 있음 |
| 기기끼리 직접 결제 | 반영 | NFC/BLE/QR 경로가 모두 존재함 |

### 근거
- 담보 생성: [CollateralApplicationService.java#L58](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/CollateralApplicationService.java#L58)
- proof 생성/큐 적재: [offlinePaySettlementExecution.ts#L86](/Users/an/work/coin_front/src/pages/offline-pay/offlinePaySettlementExecution.ts#L86)
- 서버 정산 검증: [SettlementApplicationService.java#L359](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/SettlementApplicationService.java#L359)
- 외부 정산/보상: [SettlementExternalSyncWorker.java#L92](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/SettlementExternalSyncWorker.java#L92)

## 2. 온라인 영역

### 2-1. 담보 생성 프로세스

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| Wallet + 서버에서 담보 생성 | 반영 | canonical wallet snapshot 기준으로 담보 가능 금액 계산 |
| 서버가 해당 금액 잠금 | 반영 | `offline_pay -> coin_manage` collateral lock 경로 구현 |
| Initial State 생성 | 반영 | `initialStateRoot`를 collateral에 저장 |
| 단말기(TEE) 저장 | 부분 반영 | trust-center/secure storage는 있으나 전체 collateral initial state를 명시적 TEE 원장으로 관리한다고 보긴 어려움 |
| Pay에 총 담보금 생성 | 반영 | collateral snapshot의 `lockedAmount`로 반영 |

### 근거
- canonical wallet snapshot 조회와 추가 담보 계산: [CollateralApplicationService.java#L71](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/CollateralApplicationService.java#L71)
- `initialStateRoot` 기본값 `GENESIS`: [CollateralApplicationService.java#L86](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/CollateralApplicationService.java#L86)
- 프런트 snapshot 저장: [App.tsx#L462](/Users/an/work/coin_front/src/App.tsx#L462)

### 2-2. 온라인 결제 프로세스

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 결제 발생 후 서버 즉시 검증 | 반영 | settlement submit 후 서버 검증 로직 수행 |
| 총 담보금 + 사용 가능 금액 같이 차감 | 부분 반영 | 서버 정산 이후 snapshot 기준으론 같이 맞춰지지만, 프런트 projection은 send 시 `remainingAmount`만 우선 감소 |
| Wallet = Pay 항상 동기화 | 부분 반영 | snapshot/polling 기반 정합성 유지이며 강결합 실시간 동기화로 보긴 어려움 |

### 근거
- 프런트 projection에서 send 시 `remainingAmount`만 감소: [offlinePayProjection.ts#L247](/Users/an/work/coin_front/src/shared/lib/offlinePayProjection.ts#L247)
- settlement 성공 시 collateral 차감: [SettlementApplicationService.java#L388](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/SettlementApplicationService.java#L388)
- snapshot refresh/polling: [offlinePaySnapshotStream.ts#L152](/Users/an/work/coin_front/src/shared/lib/offlinePaySnapshotStream.ts#L152)

## 3. 오프라인 영역 핵심 기술

### 3-1. 오프라인 공통 결제 프로세스

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 결제 금액 입력 | 반영 | 프런트 송금 폼과 settlement payload 생성 경로 존재 |
| 이전 상태 해시 불러오기 | 반영 | local/native spending state에서 이전 해시 조회 |
| 카운터 증가 | 반영 | monotonic counter 증가 구현 |
| 새로운 상태 해시 생성 | 반영 | SHA-256 기반 새 상태 해시 생성 |
| 사용 가능 금액 차감 | 반영 | 오프라인 projection에서 `remainingAmount` 감소 |
| 사용권(proof) 생성 | 반영 | spending proof 생성 및 queue item 포함 |

### 3-2. 특허 기반 핵심 공식

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| `New Hash = H(prev + amount + counter + device + nonce)` | 반영 | 프런트/서버 모두 같은 공식 사용 |
| 이전 상태 없으면 생성 불가 | 부분 반영 | 현재는 `GENESIS` initial state가 있으면 최초 proof 생성 가능 |
| 되돌리기 불가 | 반영 | previous hash + counter chain 검증 |
| 위조 불가 | 반영 | hash, signature, device binding, replay 검증 |

### 근거
- 해시 공식: [offlineSpendingProof.ts#L213](/Users/an/work/coin_front/src/shared/lib/offlineSpendingProof.ts#L213), [SpendingProofHashService.java#L12](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/settlement/SpendingProofHashService.java#L12)
- counter 증가 / prev hash / new hash 생성: [offlineSpendingProof.ts#L280](/Users/an/work/coin_front/src/shared/lib/offlineSpendingProof.ts#L280)
- genesis/link/counter 검증: [ProofChainValidator.java#L24](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/settlement/ProofChainValidator.java#L24)

## 4. 결제 방식별 프로세스

### 4-1. NFC 결제

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 두 기기 접촉 / 자동 연결 | 반영 | NFC 연결 단계와 connect flow 존재 |
| 사용자 금액 입력 후 즉시 proof 생성 | 반영 | NFC도 공통 settlement execution/proof 생성 경로 사용 |
| 자동 검증 후 완료 | 부분 반영 | 현장 1차 검증은 있으나 최종 확정은 서버 정산 |
| 승인 없음 / 빠른 UX | 반영 | NFC는 fast payment 흐름으로 유지 |

### 4-2. BLE 결제

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 주변 기기 탐색 | 반영 | BLE list/profile/connect 단계 존재 |
| 상대 선택 | 반영 | BLE profile 선택 단계 존재 |
| 결제 요청 전송 | 반영 | BLE payload 전송 및 request flow 존재 |
| 상대 승인 | 반영 | BLE는 receiver approval/waiting 흐름 유지 |
| proof 생성 후 검증 | 반영 | 공통 proof/settlement 경로 사용 |

### 4-3. QR 결제

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 받는 사람이 QR 생성 | 반영 | topbar QR / user QR 표시 구현 |
| 보내는 사람이 스캔 | 반영 | 전용 scan-qr 경로와 native/web scanner 구현 |
| 금액 입력 | 반영 | QR 성공 후 송금 페이지 진입 |
| 온라인이면 서버 submit / 오프라인이면 proof 생성 | 반영 | QR도 공통 settlement queue/submit 구조 사용 |
| 범용 상점/POS 대체 | 부분 반영 | store UI는 존재하나 실제 POS 정산 시나리오가 완전히 별도 엔진으로 분리된 것은 아님 |

## 5. 검증 프로세스

| 검증 항목 | 상태 | 판단 |
| --- | --- | --- |
| TEE 상태 확인 | 부분 반영 | trust center snapshot/UI는 있으나 정산 승인 필수 게이트로 항상 강제되진 않음 |
| 키 서명 상태 확인 | 반영 | trust center snapshot + device signature verification 존재 |
| 디바이스 신뢰 레벨 확인 | 부분 반영 | trust center에 등급/attestation 데이터는 있으나 settlement 승인 정책의 절대 필수 조건으로 보긴 어려움 |
| 해시 연속성 검증 | 반영 | previous hash / genesis link 검증 |
| 카운터 검증 | 반영 | genesis counter / counter gap 검증 |
| 서명 검증 | 반영 | device signature / issued proof signature 검증 |
| 중복 사용 체크 | 반영 | duplicate settlement, nonce replay, request replay, conflict detection 존재 |
| 결과 통과/거절 | 반영 | settlement status `SETTLED/REJECTED/CONFLICT/...`로 판정 |

### 근거
- proof schema, payload, issued proof, device binding, signature, conflict, chain 검증: [SettlementApplicationService.java#L459](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/SettlementApplicationService.java#L459)
- duplicate/replay/conflict: [SettlementApplicationService.java#L483](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/SettlementApplicationService.java#L483), [SettlementApplicationService.java#L505](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/SettlementApplicationService.java#L505)

## 6. 정산 프로세스

### 6-1. Sender 정산

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 오프라인 proof 제출 | 반영 | queue sync / batch submit 구현 |
| 서버가 상태 체인 검증 | 반영 | chain/signature/conflict/policy 검증 수행 |
| 총 사용 금액 계산 | 반영 | settlement 결과와 collateral 차감 반영 |
| 담보금 차감 확정 | 반영 | settlement 성공 시 차감 |
| Wallet / Pay 동기화 | 부분 반영 | 외부 sync + snapshot 갱신으로 반영, 완전 실시간 강결합은 아님 |

### 6-2. Receiver 정산

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 받은 proof 제출 또는 자동 업로드 | 부분 반영 | 수신 측 식별/verification은 있으나 sender/receiver 양측 독립 proof 업로드 구조는 명확하지 않음 |
| 서버가 동일 proof 검증 | 반영 | 제출 proof 자체는 검증됨 |
| 유효성 확인 후 자산 지급 | 부분 반영 | history 반영과 내부 정산은 존재하나 receiver 별도 독립 settlement leg가 완전히 분리된 구조는 아님 |
| 결과: 실제 돈 들어옴 확정 | 부분 반영 | 사용자 history/표시는 반영되지만 문서 설명처럼 receiver leg가 완전 독립된 엔진으로 보이진 않음 |

### 6-3. 자동/수동 정산

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 자동 정산 | 반영 | worker 기반 external sync/settlement tracking 존재 |
| 수동 정산 | 부분 반영 | 운영/admin 재처리, retry, reconciliation은 있으나 일반 사용자 수동 정산 UX는 제한적 |

### 근거
- batch submit: [SettlementApplicationService.java#L139](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/SettlementApplicationService.java#L139)
- external sync 및 history 기록: [SettlementExternalSyncWorker.java#L94](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/SettlementExternalSyncWorker.java#L94), [InternalOfflinePayHandler.java#L49](/Users/an/work/foxya_coin_service/src/main/java/com/foxya/coin/transfer/InternalOfflinePayHandler.java#L49)

## 7. 보안 설정 프로세스

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 생체 인증 / PIN | 반영 | PIN mandatory, biometric flow, offline verifier/ticket 구조 존재 |
| 자동 승인 설정 | 부분 반영 | payment approval mode와 QR/BLE 흐름은 있으나 모든 결제 방식에 동일한 자동 승인 정책 엔진은 아님 |
| 결제 제한 설정 | 반영 | payment limit, settings sheet, snapshot sync 존재 |
| 오프라인 사용 한도 설정 | 반영 | collateral remaining / usable amount 구조와 limit UI 존재 |
| 사용자 레벨 보안 역할 | 반영 | security status, trust center, transaction password, biometric registration 구현 |

## 8. 알림 프로세스

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 결제 완료 | 반영 | result alert modal, notification center logs 존재 |
| 결제 요청 | 반영 | BLE/request flow 및 알림 로그 존재 |
| 실패 알림 | 반영 | failed state / alert / dead-letter / ops alert 존재 |
| 정산 완료 | 반영 | settlement tracking 후 상태 표시 |
| 보안 경고 | 반영 | PIN lock, trust/security alerts 존재 |
| 온라인 시 동기화 | 반영 | notification center sync effect 존재 |
| 오프라인 로컬 저장 후 전달 | 부분 반영 | 일부 로컬 로그/센터 저장은 있으나 모든 알림이 별도 durable queue를 타는 구조로 통일된 것은 아님 |

## 9. 보안센터(Trust Center)

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| TEE 상태 | 반영 | trust center snapshot에 `teeAvailable`, secure hardware, attestation 값 존재 |
| 키 서명 상태 | 반영 | key signing, device binding, auth binding key 존재 |
| 오프라인 proof 생성 로그 | 반영 | `offline_pay_proof_logs` 및 프런트 proof log UI 존재 |
| 감사 / 추적 / 검증 | 반영 | trust center logs, proof logs, settlement logs, admin metrics 존재 |

### 근거
- trust center snapshot/proof logs 저장: [UserRepository.java#L916](/Users/an/work/foxya_coin_service/src/main/java/com/foxya/coin/user/UserRepository.java#L916), [UserRepository.java#L1296](/Users/an/work/foxya_coin_service/src/main/java/com/foxya/coin/user/UserRepository.java#L1296)
- 프런트 proof log 노출: [OfflinePayFlowPage.tsx#L4014](/Users/an/work/coin_front/src/pages/offline-pay/OfflinePayFlowPage.tsx#L4014)

## 10. 충돌 해결 프로세스

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 동일 담보 이중 사용 감지 | 반영 | duplicate settlement, conflict detector, replay 검사 존재 |
| 서버 우선순위 정산 | 반영 | conflict/rejected/compensation/reconciliation 정책 존재 |
| proof 정합성 비교 | 반영 | hash, counter, nonce, signature, payload consistency 비교 |
| 두 기기 담보잠김 방지 | 부분 반영 | conflict/compensation으로 사후 방지하나, 문구 그대로의 완전 사전 차단으로 보긴 어려움 |

### 근거
- duplicate/conflict 검출: [SettlementApplicationService.java#L493](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/SettlementApplicationService.java#L493), [SettlementApplicationService.java#L505](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/application/service/SettlementApplicationService.java#L505)
- conflict metrics/admin 조회: [AdminConflictController.java](/Users/an/work/offline_pay/src/main/java/io/korion/offlinepay/interfaces/http/AdminConflictController.java)

## 11. 엄밀 점검 기준 최종 결론

### 반영된 축
- 온라인 담보 생성
- 초기 상태 root 생성 및 보관
- 오프라인 hash-chain proof 생성
- monotonic counter 기반 검증
- sender 중심 정산
- 보상/리컨실
- trust center / proof log / settlement log
- NFC/BLE/QR 각 결제 흐름

### 부분 반영 축
- `서버 없이 검증`
  - 현장 1차 검증은 가능
  - 최종 권위 판정은 서버 정산
- `Wallet = Pay 항상 동기화`
  - snapshot/polling 기반 정합성
  - 완전 실시간 강결합 동기화는 아님
- `온라인 결제 시 총 담보금 + 사용가능금액 동시 차감`
  - 최종 상태는 맞춰지지만 프런트 projection은 send 시 `remainingAmount` 중심
- `Receiver 정산`
  - receiver leg/history는 존재
  - 문서 설명처럼 완전 독립된 receiver proof 정산 엔진이라고 보긴 어려움
- `TEE 저장`
  - trust center와 secure storage는 있음
  - 전체 state/collateral root가 전부 TEE 전용 원장으로 표현되진 않음

### 미반영으로 보는 축
- 현재 점검 범위에서는 `완전 미반영`으로 단정할 핵심 축은 크지 않음
- 대신 문서 표현 대비 구현 방식이 더 완화된 `부분 반영` 축이 몇 개 존재함

## 12. 요구사항 문구를 코드 현실에 맞게 다듬으면 좋은 표현

- `서버 없이 검증`
  - `현장에서는 서버 없이 1차 검증 및 결제 진행, 최종 판정은 서버 정산`
- `이전 상태 없으면 생성 불가`
  - `초기 상태(root) 없이는 생성 불가`
- `Wallet = Pay 항상 동기화`
  - `온라인 복귀 및 snapshot 동기화 시 Wallet과 Pay 정합성을 맞춤`
- `Receiver 정산`
  - `수신자 식별/이력 반영은 구현돼 있으나, sender/receiver 완전 분리 proof-leg 구조는 추가 정리가 필요`
