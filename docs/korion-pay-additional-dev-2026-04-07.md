# KORION PAY 추가 개발 항목 정리

작성일: 2026-04-07
기준 문서: `korion-pay-implementation-audit-2026-04-07.md`

> 감사 문서의 `부분 반영` 항목 전체를 대상으로, 개발 완성도 관점에서 추가 개발이 필요한 사항을 도메인별로 정리함.
> `완전 미반영`으로 단정할 항목은 없으나, 구조적 개선이 필요한 축과 기능 완성도 보완이 필요한 축으로 구분함.

---

## 1. Receiver 정산 구조 분리 ★ 우선순위 높음

### 현재 상태
- 수신자 식별/이력 반영은 존재
- sender/receiver 양측 독립 proof 업로드 구조 없음
- receiver 별도 settlement leg가 완전히 분리된 구조가 아님
- "실제 돈 들어옴 확정" 처리가 receiver 독립 엔진으로 표현되지 않음

### 필요한 개발
- [ ] **Receiver proof 업로드 경로 명시화**
  - 현재 sender가 제출한 proof 기준으로만 검증됨
  - receiver가 별도로 받은 proof를 제출하거나 자동 업로드하는 경로 구현 또는 명시화
- [ ] **Receiver settlement leg 분리**
  - `offline_pay`: receiver 기준 settlement 처리 서비스/상태 분리
  - 현재 sender leg에 종속된 구조를 sender/receiver 독립 처리로 분리
- [ ] **Receiver 자산 지급 확정 이벤트 명시화**
  - settlement 완료 후 receiver 잔액 반영을 별도 이벤트/처리 흐름으로 분리
  - `foxya_coin_service`: receiver 기준 코인 지급 확정 처리 명확화

---

## 2. TEE / 디바이스 신뢰 게이트 강화

### 현재 상태
- trust center snapshot/UI는 있으나 settlement 승인 시 TEE 확인이 필수 게이트로 강제되지 않음
- 디바이스 신뢰 레벨(attestation) 데이터는 있으나 settlement 정책과 연동되지 않음
- collateral initial state가 명시적 TEE 전용 원장으로 관리되지 않음

### 필요한 개발
- [ ] **TEE 상태 → settlement 승인 필수 게이트 연동**
  - `offline_pay`: settlement 처리 전 TEE 상태 확인을 선택이 아닌 필수 조건으로 강제
  - TEE 상태 불량 시 settlement 거절 정책 명확화
- [ ] **디바이스 신뢰 레벨 → settlement 정책 연동**
  - attestation/trust level에 따른 결제 한도 또는 승인 조건 차등 적용
  - `offline_pay` settlement policy에 trust level 분기 추가
- [ ] **Collateral initial state TEE 원장 관리 명확화**
  - `initialStateRoot` 생성/저장 시 TEE secure storage 원장으로 명시적 관리
  - trust center에 collateral state 이력 연동

---

## 3. 프런트 Projection / 동기화 개선

### 현재 상태
- 온라인 결제 시 프런트 projection은 `remainingAmount`만 우선 감소, 담보금 차감은 서버 정산 후 반영
- `Wallet = Pay` 동기화가 snapshot/polling 기반이라 실시간 강결합이 아님

### 필요한 개발
- [ ] **온라인 결제 시 담보금 + 잔액 동시 차감 projection**
  - `coin_front`: send 시 `remainingAmount`만 감소하는 것을 `lockedAmount`(담보금)도 함께 낙관적으로 감소하도록 수정
  - 서버 응답 기준으로 최종 보정하는 reconciliation 처리 포함
- [ ] **Wallet-Pay 동기화 강화**
  - polling 주기 조정 또는 server-sent event / WebSocket 기반 push 방식 검토
  - settlement 완료 이벤트 수신 즉시 snapshot 갱신 트리거 추가

---

## 4. 검증 정책 강화

### 현재 상태
- `서버 없이 검증`: 현장 1차 검증만 가능, 최종 권위는 서버 정산
- `이전 상태 없으면 생성 불가`: GENESIS가 있으면 최초 proof 생성 가능 — 완전한 "이전 상태 의존" 구조는 아님
- NFC 자동 검증 후 완료: 최종 확정은 여전히 서버 정산
- 두 기기 담보잠김 방지: conflict/compensation 사후 처리 위주, 사전 차단 없음

### 필요한 개발
- [ ] **현장 검증 범위 명확화 또는 확장**
  - 현장에서 검증 가능한 항목(hash chain, counter, signature)과 서버 필수 항목(최종 자산 반영)을 정책 문서로 명확히 분리
  - NFC 결제 완료 UX에서 "현장 1차 검증 완료 / 최종 확정 예정" 상태 표시 추가 검토
- [ ] **GENESIS 이후 proof 체인 의존성 강제**
  - `GENESIS` state 이후 proof는 반드시 이전 proof hash를 참조하도록 검증 강화
  - `ProofChainValidator`: GENESIS 직후 첫 proof의 prev hash 검증 규칙 명확화
- [ ] **담보잠김 사전 차단 정책 추가**
  - 동일 담보에 동시 결제 시도 시 서버 단에서 낙관적 락 또는 분산 락 적용 검토
  - 현재 사후 conflict 처리에 더해 담보 상태 기반 사전 차단 레이어 추가

---

## 5. 기능 완성도 보완

### 5-1. 일반 사용자 수동 정산 UX
**현재 상태**: admin/ops 재처리만 가능. 사용자 직접 재시도 UX 없음

- [ ] **사용자 정산 재시도 화면 추가**
  - `coin_front`: 정산 실패/지연 상태인 proof에 대해 사용자가 직접 재제출 가능한 UX
  - `offline_pay`: 사용자 요청 기반 proof 재처리 API 추가

### 5-2. 자동 승인 정책 통합 엔진
**현재 상태**: QR/BLE별 흐름은 있으나 결제 방식 공통 자동 승인 정책 엔진 없음

- [ ] **결제 방식 공통 자동 승인 정책 엔진 구현**
  - 결제 방식(NFC/BLE/QR)과 무관하게 동일한 승인 정책(한도, 생체 인증 여부, 신뢰 레벨)을 적용하는 공통 policy 서비스 분리
  - `offline_pay` 또는 `coin_front`에 `PaymentApprovalPolicyService` 계층 추가

### 5-3. 오프라인 알림 Durable Queue 통합
**현재 상태**: 일부 로컬 로그/센터 저장은 있으나 알림 전달이 durable queue 구조로 통일되지 않음

- [ ] **오프라인 알림 durable queue 통합**
  - 온라인 복귀 시 오프라인 중 미전달 알림을 순서 보장하여 전달하는 큐 구조 통합
  - `coin_front`: 오프라인 알림 로컬 저장 → 온라인 복귀 시 자동 전송 처리

### 5-4. QR POS 정산 엔진 분리
**현재 상태**: store UI는 있으나 POS 정산 시나리오가 공통 settlement 구조에 혼재

- [ ] **QR POS 정산 독립 엔진 분리**
  - 개인 간 송금과 상점/POS 정산 시나리오를 별도 settlement 처리 경로로 분리
  - 상점 정산 특화 로직(다건 집계, 정산 주기 등) 독립화

---

## 요약

| 도메인 | 항목 수 | 우선순위 |
|--------|---------|---------|
| Receiver 정산 구조 분리 | 3 | 높음 |
| TEE / 디바이스 신뢰 게이트 | 3 | 높음 |
| 프런트 Projection / 동기화 | 2 | 중간 |
| 검증 정책 강화 | 3 | 중간 |
| 기능 완성도 보완 (UX/구조) | 4 | 낮음 |

> Receiver 정산 구조 분리와 TEE 게이트 강화는 설계 방향 확인이 선행되어야 함.
> 프런트 Projection 개선은 서버 정산 응답 구조 변경 없이 프런트만 수정 가능한 범위부터 시작 가능.
