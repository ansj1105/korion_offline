# Cross-Service Communication Contract

## 1. 목적

- `offline_pay`, `coin_manage`, `foxya`, `coin_publish`, `coin_csms`, `coin_front`를 같은 용어로 부른다.
- 서비스 간 금액 기준, 상태 기준, 책임 경계를 고정한다.
- 사용자와 운영자가 보는 값, 원장 값, 스냅샷 값을 섞지 않는다.

## 2. 저장소 / 서비스 별칭

- `오프페`
	- `offline_pay`
- `원장`
	- `coin_manage`
- `폭시`
	- `foxya_coin_service`
- `퍼블리셔`
	- `coin_publish`
- `관리브리지`
	- `coin_csms`
- `앱`
	- `coin_front`
- `ios브랜치`
	- `coin_front`의 `ios`
- `develop프론트`
	- `coin_front`의 `develop`

## 3. 책임 경계

### 3.1 foxya

- 사용자 앱에 보이는 가용 총 자산 기준
- 거래내역 / history / projection 기준
- `available KORI` canonical snapshot source

### 3.2 coin_manage

- canonical ledger source of truth
- `available`, `offline_pay_pending`, `withdraw_pending` 원장 기준
- 담보 락/해제, 정산 반영, 역분개 보상 책임

### 3.3 offline_pay

- 오프라인 스냅샷, 큐, saga, workflow orchestration
- 온라인 복귀 후 최종 정합성 판정

### 3.4 coin_publish

- 체인 스캔 / 발행 / worker
- publish event에 workflow/saga 메타 포함

### 3.5 coin_csms

- 관리자 bridge
- workflow / saga / reconciliation summary 노출

### 3.6 coin_front

- 앱 표시 / 로컬 큐 / 로컬 projection
- 서버 확정값과 로컬 pending 반영값 구분 표시

## 4. 금액 기준

### 4.1 총 자산

- `foxya available KORI`
- 사용자에게 보이는 canonical total
- 채굴 / 레퍼럴 / 에어드랍 / 내부 정산 반영분 포함
- `offline_pay` 승인 담보금은 제외한 값

### 4.2 총담보금

- 서버가 승인한 오프라인 담보 총액
- `offline_pay` snapshot / `coin_manage offline_pay_pending`과 연결

### 4.3 추가 담보 전환 가능 금액

- `foxya available KORI`
- 오프라인/온라인 모두 담보 전환 상한 계산 기준

### 4.4 총 원천 자산

- `foxya available KORI + offline_pay approved collateral locked KORI`
- 운영 reconciliation과 원장 정합성 비교 기준

### 4.5 오프라인 결제 가능 금액

- `총담보금`에서 사용된 금액과 로컬 pending 사용분을 반영한 값
- receiver가 받은 KORI는 수취 확정 직후 자동 포함하지 않는다.
- 받은쪽 `Settle now` / `Auto settle`이 기존 `COLLATERAL_TOPUP` 경로로 성공한 뒤에만 오프라인 결제 가능 금액에 포함한다.

### 4.6 락원장

- `coin_manage available / offline_pay_pending / withdraw_pending`
- 원장 이동과 정산 보상 기준
- 정산 보상은 `offline_pay` saga가 `LEDGER_COMPENSATION_REQUESTED`를 발행하고, `coin_manage /api/internal/offline-pay/settlements/compensate`가 역분개한다.
- receiver history 실패처럼 sender foxya history가 이미 반영된 뒤 실패한 경우에는 `OFFLINE_PAY_COMPENSATION` history를 함께 기록해 foxya 사용자 지갑 반영도 되돌린다.

### 4.7 receiver 수취금 담보 전환

- 수취 확정:
  - `OFFLINE_PAY_RECEIVE` history와 `foxya user_wallets.balance` 반영
- 담보 전환:
  - 앱의 받은쪽 `Settle now` / `Auto settle` 실행
  - 완료된 수취 합계를 `COLLATERAL_TOPUP` 큐로 등록
  - `offline_pay -> coin_manage collateral lock` 성공 후 총담보금과 오프라인 결제 가능 금액에 반영
- 정책상 receiver 수취금을 settlement finalize 단계에서 담보로 직접 편입하지 않는다.

## 5. 상태 별칭

### 5.1 workflowStage

- `로컬적재`
	- `LOCAL_QUEUED`
- `서버접수`
	- `SERVER_ACCEPTED`
- `정산접수`
	- `SETTLEMENT_ACCEPTED`
- `원장락완료`
	- `COLLATERAL_LOCKED`
- `담보해제완료`
	- `COLLATERAL_RELEASED`
- `원장반영완료`
	- `LEDGER_SYNCED`
- `히스토리반영완료`
	- `HISTORY_SYNCED`
- `실패`
	- `FAILED`
- `데드레터`
	- `DEAD_LETTERED`

### 5.2 sagaStatus

- `접수`
	- `ACCEPTED`
- `처리중`
	- `PROCESSING`
- `부분적용`
	- `PARTIALLY_APPLIED`
- `완료`
	- `COMPLETED`
- `보상필요`
	- `COMPENSATION_REQUIRED`
- `보상중`
	- `COMPENSATING`
- `보상완료`
	- `COMPENSATED`
- `실패`
	- `FAILED`
- `데드레터`
	- `DEAD_LETTERED`

## 6. 동기화 기준

### 6.1 오프라인

- 앱은 마지막 온라인 `foxya available KORI snapshot` 기준으로 계산한다.
- `topup / release / send / receive`는 로컬 큐에 먼저 적재한다.
- 오프라인 동안은 로컬 projection으로 표시한다.

### 6.2 온라인 복귀

- 큐를 서버로 전송한다.
- `offline_pay -> coin_manage -> foxya` saga로 최종 판정한다.
- 성공분만 확정한다.
- 실패분은 compensation 또는 제거 대상으로 돌린다.
- 처리 후 새 snapshot을 다시 받는다.

### 6.3 온라인 실시간 반영

- 추천 기준:
	- 온라인에서는 요청 후 즉시 서버 확정값 재조회
	- `wallet-refresh` 같은 이벤트 신호가 오면 snapshot 재조회
- `foxya available KORI` 또는 `offline collateral` 변동이 있으면 `offline_pay snapshot`도 다시 발급한다.

## 7. 기대 동작

- 오프라인 전송은 온라인 복귀 후 정상 처리될 수 있다.
- 단 아래 조건이 맞아야 한다.
	- snapshot 기준 상한을 넘지 않아야 한다.
	- 서버 business rule 위반이 없어야 한다.
	- 온라인 복귀 후 outbox / worker / saga가 정상 동작해야 한다.

실패 시 처리:

- `TRANSPORT`
	- retry 유지
- `BUSINESS`
	- 최종 실패
- `SYSTEM`
	- dead-letter 또는 운영 개입

## 8. 대화 예시

- `오프페 스냅샷 기준 다시 봐줘`
- `원장 락원장하고 폭시 총자산 gap 확인해줘`
- `퍼블리셔 워커 사가 상태 확인해줘`
- `앱 허브에서 총담보금/오프결제가용 문구 손봐줘`
