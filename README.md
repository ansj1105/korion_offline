# Offline Pay Backend

병합 정리된 [PRD.md](/Users/an/work/offline_pay/docs/PRD.md)를 기준으로 만든 오프라인 담보 결제 PoC 백엔드입니다.

## 선택한 스택
- Runtime: Java 17 + Spring Boot
- Build: Gradle Wrapper
- HTTP: Spring MVC
- DB: PostgreSQL + Flyway
- Event/worker: Redis Streams
- 배포: EC2 Docker Compose
- 외부 연동: `coin_manage` 원장 adapter + `fox_coin` 거래내역 adapter

정산/검증/상태 전이 로직이 핵심이라 강한 타입과 트랜잭션 경계가 더 중요한 구조로 판단했고, 그래서 Java/Spring으로 전환했습니다.

## 구조
- `src/main/java/io/korion/offlinepay/domain`: 상태 enum과 도메인 모델
- `src/main/java/io/korion/offlinepay/application`: 유스케이스 서비스와 포트
- `src/main/java/io/korion/offlinepay/infrastructure`: JDBC, Redis, 외부 프록시 구현체
- `src/main/java/io/korion/offlinepay/interfaces`: Spring MVC controller와 request DTO
- `src/main/resources/db/migration`: Flyway 마이그레이션

`prd2`에서 추가된 `Risk Engine`, `기기 단위 정책`, `관리자 충돌 로그` 요구를 반영해 스키마에 `device_risk_profiles`, `settlement_conflicts`, `collateral_locks.remaining_amount / initial_state_root / policy_version`를 포함했습니다.

## 시작
```bash
cp .env.example .env
./gradlew test
docker compose up -d --build
```

개발 실행:
```bash
./gradlew bootRun
```

## 1차 API
- `GET /health`
- `POST /api/devices/register`
- `POST /api/collateral`
- `POST /api/settlement-batches`
- `POST /api/settlement-batches/:batchId/finalize`

## 외부 연동 경계
- `POST /api/collateral` 생성 시 `coin_manage`에 자산 잠금 요청
- 정산 finalize 시 `coin_manage`에 canonical ledger finalize 전달
- 정산 finalize 시 `fox_coin`에 사용자 거래내역 기록 요청
- 내부 계약 문서는 [internal-integrations.md](/Users/an/work/offline_pay/docs/internal-integrations.md)에 정리

## 배포 메모
- EC2에서는 `app-api`, `app-worker`, `postgres`, `redis` 조합으로 구성
- 실운영에서는 `postgres`를 별도 RDS로 분리 가능
- worker는 Redis Streams consumer group 기반으로 수평 확장 가능
- 배치 실패는 pending 유지 후 reclaim 하며, `max attempts` 초과 시 dead-letter stream으로 격리
