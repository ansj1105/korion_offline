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
