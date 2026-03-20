# Offline Payment Completion Checklist

## 1. Current Status

### coin_front
- 완료
  - 오프라인 fallback 진입
  - QR/NFC PoC 화면
  - Android 네이티브 QR 스캐너
  - iOS 네이티브 QR 스캐너
  - iOS NFC entitlement 수정
  - 오프라인 secure-state 기본 경로
  - 로컬 큐 저장 및 온라인 복귀 sync 트리거
- 검증 완료
  - `npm run build`
  - `npm test -- src/app/routes/__tests__/routeModules.test.ts`
  - Android `./gradlew :app:compileDebugJavaWithJavac`
  - iOS `xcodebuild ... build`
- 미완료
  - iOS `archive` 검증
  - 실기기 기준 NFC/QR end-to-end 검증
  - 오프라인 결제 전체 플로우를 거래내역/보안설정/운영 UI와 더 강하게 연결

### offline_pay
- 완료
  - proof schema 검증
  - state hash chain 검증
  - device binding 검증
  - server-side 정합성 판정
  - admin metrics/conflict/dead-letter 기본 운영 API
- 검증 완료
  - `./gradlew test`
- 미완료
  - `coin_manage`의 기존 출금 진입점과 최종 계약 고도화
  - 실제 체인 출금 목적지 정보 포함 계약 확정
  - receiver-side 검증과 secure hardware 전제 문서 고도화

### coin_manage
- 완료
  - `is_test = 1` 사용자 mainnet 출금 차단
  - `proofFingerprint` 보존
  - `offline_pay.settlement.finalized` consumer
  - 실행 직전 `proofFingerprint` 재검증 경로
- 검증 완료
  - `npm run build`
  - offline pay 관련 테스트
- 미완료
  - `toAddress` 포함 계약 이후 기존 withdraw 경로 완전 연결
  - `TRC / ETH / BTC / XRP`별 실체인 출금 최종 execution 연결
  - outbox/event 기준 재시도 및 실패 분류 고도화

## 2. Branch / Build Facts

### coin_front branch state
- `develop`: `4e2a436`
- `ios`: `68eb672`

### what is actually done
- `coin_front` 변경은 `develop`, `ios` 둘 다 전파됨
- iOS는 Xcode 경로를 명시해서 `build` 성공까지 확인함
- 단, `archive`는 아직 별도 검증하지 않음

## 3. System Boundary

오프라인 결제는 즉시 실체인 송금이 아니다.

- 오프라인 구간
  - NFC/QR로 상대 식별
  - 단말 내부 secure-state로 proof 생성
  - 로컬 큐 저장
  - 상태는 `LOCAL_ONLY` 또는 `SYNC_PENDING`
- 온라인 복귀 후
  - `offline_pay`가 proof 업로드 수신
  - 서버 정합성 검증
  - 성공 시 `coin_manage`로 canonical ledger / execution 이벤트 전달
  - 필요 시 기존 withdraw 진입점으로 연결

핵심 원칙:
- 근거리 통신으로 발생한 거래는 내부원장 이동 요청이다
- 실제 네트워크 트랜잭션은 온라인 상태에서만 기존 출금 로직으로 들어간다
- `users.is_test = 1` 사용자는 testnet에서만 실행 가능하다

## 4. Highest Priority Remaining Work

### P0
- `offline_pay -> coin_manage` finalize 계약에 `toAddress`, `network`, `token`, `executionType`를 명시적으로 포함
- `coin_manage` consumer가 해당 payload를 기존 withdraw service 진입점으로 연결
- execution 결과를 `fox_coin` 거래내역과 관리자 화면에 반영

### P1
- iOS `archive` 검증
- Android/iOS 실기기 NFC/QR 검증
- 실기기 권한 팝업, 카메라 화면, NFC 오버레이 UX 점검

### P2
- 관리자 화면에 mainnet/testnet, network/token, execution status 필터 강화
- 실패 사유별 운영 지표와 재처리 정책 분리
- secure-state / receiver verification 문서 보강

## 5. Recommended Next Steps

1. `offline_pay` internal finalize contract 확장
2. `coin_manage` withdraw entry 연결
3. `coin_front` 실기기 테스트
4. iOS archive 검증
5. 관리자 운영 화면 고도화
