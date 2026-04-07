# Offline Pay Agent Rules

## Architecture Rules
- `offline_pay`는 계층형 구조를 유지한다.
- `interfaces`는 HTTP 입출력만 담당한다.
- `application`은 유스케이스와 트랜잭션 경계를 담당한다.
- `domain`은 상태 enum과 도메인 모델을 소유한다.
- `infrastructure`는 DB, Redis, 외부 서비스 구현체를 둔다.
- 생성 책임이 반복되면 `factory`로 승격한다.

## External Service Rules
- `coin_manage`, `foxya_coin_service` 같은 외부 서비스 연동은 반드시 `application/port` 인터페이스를 먼저 정의하고 `infrastructure/adapter` 구현체로 연결한다.
- 서비스 코드에서 외부 API 클라이언트를 직접 생성하지 않는다.
- 외부 서비스 호출 URL, 키, timeout은 설정 객체를 통해 주입한다.
- `coin_manage`는 canonical ledger와 정산 원장 책임을 가진다.
- `foxya_coin_service`는 사용자 거래내역과 앱 표시용 history 책임을 가진다.
- 외부 서비스 DB에 직접 쓰지 않고, 내부 API 또는 명시적 bridge adapter로만 기록한다.

## Persistence Rules
- persistence 레이어는 raw SQL 문자열 난립 대신 내부 `QueryBuilder` 유틸을 우선 사용한다.
- row mapping은 repository 바깥 `infrastructure/persistence/mapper` 패키지로 분리한다.
- repository는 SQL 조합과 저장소 책임만 가진다.

## DI Rules
- 서비스에서 협력 객체를 직접 `new` 하지 않는다.
- infra 구현체 생성은 Spring bean 조립 또는 factory config에서 수행한다.
- 예외, DTO, record 생성은 허용하지만 외부 의존 구현체 직접 생성은 금지한다.
- 배치 생성, 상태 전이 결과 생성, HTTP 응답 생성도 가능한 한 factory에 위임한다.

## Deployment Rules
- `offline_pay`는 `foxya`와 분리한다.
- 운영 기준으로 `coin_manage`와도 별도 EC2를 권장한다.
- `offline_pay`는 전용 Postgres, 전용 Redis를 사용한다.
- `coin_manage` 또는 `foxya` DB를 공유하지 않는다.
- 같은 VPC private network로 서비스 간 통신하고, 외부 진입은 ALB 또는 internal ALB로 제한한다.

## Current Infra Findings
- `foxya_coin_service`는 자체 Postgres/Redis와 `db-proxy`를 가진 별도 Compose 스택이다.
- `coin_manage`도 자체 Postgres/Redis를 가진 별도 Compose 스택이다.
- 따라서 현재 운영 구조는 “개별 인스턴스 + 개별 DB”에 가깝고, `offline_pay`도 같은 원칙으로 가야 한다.

## Ops Notes
- `offline_pay` 운영 서버는 현재 `98.91.96.182`이고 앱 루트는 `/var/www/korion_offline`이다.
- `offline_pay` 서버 접속은 현재 `ubuntu@98.91.96.182` + `korion.pem` 경로로 확인됐다.
- `coin_manage` 운영 서버는 현재 `54.83.183.123`이고 앱 루트는 `/var/www/korion`이다.
- `foxya_coin_service` 운영 서버는 현재 `52.200.97.155`이고 앱 루트는 `/var/www/fox_coin`이다.
- 서비스 재배포 시 먼저 원격 앱 루트와 Docker Compose 위치를 확인하고, 이후 `git pull` + `sudo docker compose up -d --build` 순서를 사용한다.
- Telegram 같은 운영 비밀값은 레포에 커밋하지 않고 각 서버 `.env`에만 유지한다.
- `coin_manage` 운영 Postgres는 standby가 실제로 붙어 있지 않으면 `synchronous_standby_names`를 비워야 한다. standby 없이 `FIRST 1 (...)`가 남아 있으면 commit이 `SyncRep`에 걸리고 `offline_pay` collateral `lock/release`가 advisory lock chain 뒤에서 timeout 난다.
- `offline_pay` collateral dead-letter가 `application/octet-stream` parse error에서 `Read timed out`로 바뀌면, 우선 `coin_manage` Postgres의 `show synchronous_standby_names;`, `pg_stat_replication`, `pg_stat_activity`를 확인한다.

## Offline Pay Policy Rules
- 오프라인 페이 요구사항 구현 시 UI 데모용 하드코딩 business 데이터를 기본 동작에 남기지 않는다.
- 테스트용 peer, 사용자, 기기, 금액 샘플이 필요하면 `test mode` 또는 fixture 경계 안으로 격리한다.
- 오프라인 결제는 `원장 기반 pay`이며, 자동 실출금이 아니라 내부원장/정합성 검증/배치 처리 구조를 유지한다.
- `online/offline` 상태와 무관하게 오프라인 페이 화면 진입은 가능해야 하고, 최종 정합성은 온라인 복귀 후 서버가 판정한다.
- 오프라인 페이 프런트 디자인 기준은 `/Users/an/Downloads/pages/offline-pay`이다.
- `container`, `app-wrapper`, `app-container` 충돌 방지에 필요한 예외를 제외하면 다운로드본 렌더를 우선 1:1로 맞춘다.
- 기능 로직은 현재 브랜치를 유지하되, 마크업/CSS는 다운로드본을 source of truth로 본다.

## Frontend Structure Rules
- `coin_front` 오프라인 페이 화면은 FSD 기준으로 정리한다.
- `pages`는 route entry와 composition만 담당하고, 비즈니스 로직/상태 전이/정산 추적/인증 흐름을 계속 쌓아두지 않는다.
- 재사용 가능한 오프라인 페이 로직은 `features/offline-pay-*`, `entities/offline-pay-*`, `widgets/offline-pay-*`, `shared/lib`로 승격한다.
- `OfflinePayFlowPage.tsx` 같은 대형 page에는 인증, QR/BLE/NFC 연결, settlement tracking, settings sheet UI를 계속 누적하지 않는다. 새 코드를 추가할 때는 먼저 분리 가능한 경계를 찾는다.
- UI 분리만으로 FSD 완료로 보지 않는다. `model/lib/ui` 경계를 같이 맞추고, 상태 전이와 side effect가 page에 남아 있으면 계속 승격한다.
- `handleApproveCurrentSecurity`, `handleCreateRequestAfterAuth`, `handleAdvanceToConfirm`, result-state builder, overlay orchestration, stage props assembly 같은 고밀도 로직은 page에 남기지 말고 feature model 또는 hook으로 승격한다.
- `offline-pay-session`, `offline-pay-request`, `offline-pay-peer`, `offline-pay-settlement`는 우선 `entities/offline-pay-*` 후보로 검토한다.
- `send/cancel/reject/view/block` 같은 사용자 액션 흐름은 `features/offline-pay-*`로 올리고, page는 그 결과를 연결만 한다.
- 화면 조합과 프레젠테이션은 `widgets/offline-pay-*`에 두고, widget이 page 내부 상태 구조를 직접 알지 않게 props contract를 먼저 정리한다.
- 다음 분리 작업 전에는 `entity`, `feature`, `widget` 경계를 먼저 짧게 정리하고, 그 기준에서 벗어나는 임시 분리를 남기지 않는다.
- 아직 처리되지 않은 우선 정리 대상은 overlay layer, entity 경계(`session/request/peer`), settlement state entity, page 최종 route-composition 축소다.

## QR Flow Rules
- QR 스캔 단계의 프런트 책임은 `형식상 유효한 QR인지` 확인하고 송금 흐름에 진입시키는 것까지로 제한한다.
- QR payload는 수신자를 식별하는 최소값만 담고, 표시용 이름/모드/금액/설정값 같은 부가 필드를 계속 늘리지 않는다.
- QR 스캔 성공은 결제 성공이 아니다. 스캔 직후 사용자에게 성공 확정 문구를 보여주지 않는다.
- QR 송금은 온라인이면 서버 submit, 오프라인이면 로컬 큐 적재 후 온라인 복귀 시 submit 구조를 유지한다.
- 최종 성공/실패/보상/리컨실 판정은 `offline_pay`와 `coin_manage/hub`가 담당하고, 프런트는 `settlementId` 기반 상태 추적 결과를 반영한다.
- QR에서 BLE/NFC식 `receiver approval`, 양방향 연결 보장, 수신자 자동 화면 전환을 기본 전제로 두지 않는다.

## Offline Pay UX State Rules
- QR 인식 성공 시점은 `송금 페이지 진입` 또는 `유효한 QR 확인` 수준으로만 표현하고 `성공`으로 표기하지 않는다.
- 송금 버튼 직후 상태는 `정산 대기 중` 또는 `오프라인 저장됨`처럼 보류 상태로 표시한다.
- `정산 완료` 또는 `Payment Successful`은 서버 정산 완료 시점에만 표시한다.
- `REJECTED`, `EXPIRED`, `CONFLICT` 등 서버 판정 실패는 `정산 실패`로 표시한다.
- 후속 외부 sync 실패는 사용자에게 기본적으로 `정산 지연` 또는 `처리 중 문제 발생`으로 표현하고, 내부적으로는 compensation/reconciliation 흐름으로 다룬다.

## Concurrent Work Rules
- 사용자가 같은 워크트리에서 병행 작업 중이면, 먼저 `git status --short`로 충돌 가능 범위를 확인한다.
- 사용자가 동시에 리팩터링 중인 영역은 대형 일괄 수정 대신 작은 write scope 단위로 나눈다.
- 로컬 워크트리에 사용자 이동/삭제 드리프트가 있으면, 그 파일들을 되돌리거나 정리하지 않는다.
- 병행 작업 중 전체 빌드가 사용자 드리프트 때문에 깨지면, 그 사실을 명시하고 내 변경 파일 범위 검증 또는 clean server build 기준으로 판단한다.
- 사용자가 같이 작업 중일 때는 커밋/배포/iOS sync 전에 현재 워크트리 드리프트가 내 작업 범위를 침범하는지 다시 확인한다.
- 내가 추가하는 새 규칙이나 구조 변경은 사용자의 현재 리팩터 방향과 충돌하지 않도록 FSD 경계, widget 분리, helper 분리 수준으로 제한한다.
- 사용자가 동시에 작업 중인 상태에서 unrelated drift가 크면, 빌드가 되더라도 내 작업 범위를 넘는 자동 커밋/배포를 기본값으로 진행하지 않는다.
- 병행 작업 중에는 `apply_patch` 범위를 가능한 한 좁게 유지하고, 대규모 rename/move는 명시적 요청이 있을 때만 한다.

## Cross Repo Change Rules
- 오프라인 페이 변경은 `offline_pay`만 보지 말고 `coin_manage`, `foxya_coin_service`, `coin_csms`, `coin_publish`, `coin_front`까지 영향 범위를 같이 본다.
- API contract, 상태값, reason code, worker 흐름이 바뀌면 연동 서비스와 관리자 화면도 함께 갱신한다.
- DB 스키마가 바뀌면 반드시 Flyway 또는 해당 저장소의 migration 체계를 같은 작업에서 반영한다.
