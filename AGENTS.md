# Offline Pay Agent Rules

## Architecture Rules
- `offline_pay`는 계층형 구조를 유지한다.
- `interfaces`는 HTTP 입출력만 담당한다.
- `application`은 유스케이스와 트랜잭션 경계를 담당한다.
- `domain`은 상태 enum과 도메인 모델을 소유한다.
- `infrastructure`는 DB, Redis, 외부 서비스 구현체를 둔다.
- 생성 책임이 반복되면 `factory`로 승격한다.

## External Service Rules
- `coin_manage`, `foxya` 같은 외부 서비스 연동은 반드시 `application/port` 인터페이스를 먼저 정의하고 `infrastructure/adapter` 구현체로 연결한다.
- 서비스 코드에서 외부 API 클라이언트를 직접 생성하지 않는다.
- 외부 서비스 호출 URL, 키, timeout은 설정 객체를 통해 주입한다.
- `coin_manage`는 canonical ledger와 정산 원장 책임을 가진다.
- `fox_coin`은 사용자 거래내역과 앱 표시용 history 책임을 가진다.
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

## Offline Pay Policy Rules
- 오프라인 페이 요구사항 구현 시 UI 데모용 하드코딩 business 데이터를 기본 동작에 남기지 않는다.
- 테스트용 peer, 사용자, 기기, 금액 샘플이 필요하면 `test mode` 또는 fixture 경계 안으로 격리한다.
- 오프라인 결제는 `원장 기반 pay`이며, 자동 실출금이 아니라 내부원장/정합성 검증/배치 처리 구조를 유지한다.
- `online/offline` 상태와 무관하게 오프라인 페이 화면 진입은 가능해야 하고, 최종 정합성은 온라인 복귀 후 서버가 판정한다.

## Cross Repo Change Rules
- 오프라인 페이 변경은 `offline_pay`만 보지 말고 `coin_manage`, `foxya_coin_service`, `coin_csms`, `coin_publish`, `coin_front`까지 영향 범위를 같이 본다.
- API contract, 상태값, reason code, worker 흐름이 바뀌면 연동 서비스와 관리자 화면도 함께 갱신한다.
- DB 스키마가 바뀌면 반드시 Flyway 또는 해당 저장소의 migration 체계를 같은 작업에서 반영한다.
