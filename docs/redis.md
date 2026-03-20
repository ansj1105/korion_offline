5. Redis Streams consumer 구조 설계

PRD에서 이벤트 처리는 Redis Streams로 잡혀 있다.
그래서 단순 queue가 아니라 consumer group 기반 정산 파이프라인으로 설계하는 게 맞다.

5-1. 스트림 구성

권장 스트림:

stream:settlement:requested

stream:settlement:result

stream:settlement:conflict

stream:settlement:dead-letter

역할

requested: API가 업로드 후 정산 작업 enqueue

result: worker가 batch 처리 결과 publish

conflict: conflict 발생 시 관리자/후속 처리용

dead-letter: 최대 재시도 초과 배치 격리용

5-2. consumer group
stream:settlement:requested
  └─ group: settlement-workers
       ├─ consumer-1
       ├─ consumer-2
       └─ consumer-3
5-3. 메시지 구조
requested
{
  "batchId": "batch_001",
  "uploaderType": "SENDER",
  "uploaderDeviceId": "device_abc",
  "requestedAt": "2026-03-20T12:00:00Z"
}
result
{
  "batchId": "batch_001",
  "status": "PARTIALLY_SETTLED",
  "settledCount": 3,
  "failedCount": 1,
  "processedAt": "2026-03-20T12:00:05Z"
}
conflict
{
  "batchId": "batch_001",
  "voucherId": "voucher_999",
  "collateralId": "col_123",
  "conflictType": "CHAIN_FORK",
  "severity": "HIGH",
  "createdAt": "2026-03-20T12:00:03Z"
}
5-4. 처리 흐름
API 서버

/api/settlements 요청 수신

settlement_batches insert

offline_payment_proofs insert

Redis Stream XADD stream:settlement:requested

202 Accepted 반환

Worker

XREADGROUP GROUP settlement-workers consumer-1 ...

batchId 읽음

DB에서 proofs 로드

SettlementService.settleProofBatch(...)

결과 저장

XADD stream:settlement:result

conflict 있으면 XADD stream:settlement:conflict

성공 시에만 XACK

처리 실패 시 pending 유지

idle timeout 초과 pending 은 다른 worker 가 XCLAIM 으로 회수

최대 재시도 초과 시 stream:settlement:dead-letter publish 후 XACK

5-5. 재시도 / 장애 처리

이 부분이 중요하다.

기본 원칙

XACK는 성공 후에만

실패 시 pending entry에 남겨둠

다른 consumer가 XAUTOCLAIM으로 회수 가능

재시도 전략

attempt_count를 settlement_batches.detail 또는 별도 컬럼에 저장

3회 초과 시 FAILED

stream:settlement:dead-letter로 이동 가능

현재 구현

attemptCount는 settlement_batches.summary jsonb에 누적

worker 기본값
- claim idle: 60000ms
- max attempts: 3

5-6. 권장 Redis 명령 흐름
group 생성
XGROUP CREATE stream:settlement:requested settlement-workers $ MKSTREAM
생산
XADD stream:settlement:requested * batchId batch_001 uploaderType SENDER uploaderDeviceId device_abc
소비
XREADGROUP GROUP settlement-workers consumer-1 COUNT 10 BLOCK 5000 STREAMS stream:settlement:requested >
ack
XACK stream:settlement:requested settlement-workers 1710000000000-0
장시간 미처리 reclaim
XAUTOCLAIM stream:settlement:requested settlement-workers consumer-2 60000 0-0 COUNT 50

현재 코드 구현은 Spring Data Redis 제약상 pending 조회 후 XCLAIM 방식으로 동일 흐름을 만든다.
5-7. 운영 포인트
배치 단위로 처리

proof 개별 event로 흩뿌리지 말고 batchId 기준으로 밀어라.
정산은 배치 컨텍스트가 중요하다.

result stream 분리

API polling 외에도 이후 websocket/admin 알림으로 확장 가능

conflict stream 분리

정산 결과와 충돌 후속 조치는 성격이 다름
분리해야 운영이 편하다
