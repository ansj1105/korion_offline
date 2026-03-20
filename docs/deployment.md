# Offline Pay 배포 권장안

## 결론

`offline_pay`는 `foxya` 인스턴스와 합치지 말고, 현재 `korion` 인스턴스와도 운영 기준으로는 분리하는 것이 맞다. 가장 안전한 배치는 같은 VPC 안의 별도 EC2에 `api + worker + singleton ops + dedicated postgres + dedicated redis`를 두는 형태다.

## 권장 토폴로지

### 1. EC2 배치
- `offline-pay-api`: 2대 이상, ALB 뒤 stateless 확장
- `offline-pay-worker`: 2대 이상, Redis Streams consumer group 기반 확장
- `offline-pay-ops`: 1대, reconciliation, conflict scan, retry recovery 같은 singleton 작업 전용
- `postgres`: 전용 인스턴스 또는 RDS
- `redis`: 전용 인스턴스 또는 ElastiCache, 최소한 전용 컨테이너

### 2. 네트워크
- `coin_manage`, `foxya`, `offline_pay`는 같은 VPC의 private subnet 배치 권장
- 내부 통신은 private DNS 또는 private IP 기반
- public ingress는 ALB 또는 internal ALB를 통해 제한

### 3. 서비스 경계
- `offline_pay -> coin_manage`: collateral lock / settlement finalize 프록시 호출
- `offline_pay -> foxya`: 직접 결합보다 `coin_manage` 또는 명시적 bridge 경유 권장
- DB와 Redis는 절대 기존 `coin_manage`와 공유하지 않음

## 왜 분리해야 하나

- 정산/검증 CPU 부하가 커지면 `coin_manage`의 출금/운영 API와 장애 도메인을 공유하게 된다.
- Redis Streams worker와 배치 재처리는 burst 부하가 심할 수 있다.
- Postgres와 Redis를 공유하면 상태 원본 경계와 장애 전파 범위가 흐려진다.
- 해시체인 검증, conflict handling, 수동 재처리 로직은 장기적으로 별도 확장 포인트다.

## PoC 예외안

PoC 단계에서는 `korion` EC2 한 대에 같이 올리는 임시안도 가능하다. 다만 아래는 반드시 분리해야 한다.

- Docker Compose project name
- Postgres volume
- Redis
- env 파일
- container network

`foxya` 인스턴스에 합치는 것은 비추천이다. 공개 API와 레거시 앱 계층과 failure domain을 공유하게 된다.
