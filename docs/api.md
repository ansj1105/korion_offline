1-1. OpenAPI YAML
openapi: 3.0.3
info:
  title: Offline Collateral Payment API
  version: 1.0.0
  description: |
    담보 기반 오프라인 디지털 자산 결제 시스템 API.
servers:
  - url: https://api.example.com

tags:
  - name: Device
  - name: Collateral
  - name: Settlement
  - name: ClientTrace
  - name: Admin

paths:
  /api/snapshots/current:
    get:
      tags: [Collateral]
      summary: 현재 오프라인페이 snapshot 조회
      description: |
        디바이스, 담보, 발급 proof, Foxya wallet snapshot을 함께 반환한다.
        collateral projection은 서버 기준 현재 온라인 담보금과 오프라인 스냅샷 동기화를 위한 `collateralTotal`, `unsettledOutgoing`, `availableForPay`를 포함한다.
      operationId: getCurrentOfflinePaySnapshot
      parameters:
        - in: query
          name: userId
          required: true
          schema:
            type: integer
            format: int64
        - in: query
          name: deviceId
          required: true
          schema:
            type: string
        - in: query
          name: assetCode
          required: false
          schema:
            type: string
            default: KORI
      responses:
        '200':
          description: 현재 snapshot
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CurrentSnapshotResponse'

  /api/offline-pay/client-traces:
    post:
      tags: [ClientTrace]
      summary: BLE/NFC 클라이언트 trace 요약 및 JSON 파일 텔레그램 전송
      operationId: recordClientTrace
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [traceId]
              properties:
                traceId:
                  type: string
                sessionId:
                  type: string
                failureCode:
                  type: string
                flow:
                  type: string
                status:
                  type: string
                stage:
                  type: string
                entryAction:
                  type: string
                messageType:
                  type: string
                routePeerId:
                  type: string
                localSagaStatus:
                  type: string
                nativeError:
                  type: string
                deviceModel:
                  type: string
                appVersion:
                  type: string
                platform:
                  type: string
                userId:
                  type: integer
                  format: int64
                deviceId:
                  type: string
                message:
                  type: string
                metadata:
                  type: object
                steps:
                  type: array
                  maxItems: 300
                  items:
                    type: object
                createdAt:
                  type: string
      responses:
        '202':
          description: trace 접수 또는 dedupe throttle 처리

  /api/devices/register:
    post:
      tags: [Device]
      summary: 기기 등록 및 공개키 등록
      operationId: registerDevice
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RegisterDeviceRequest'
      responses:
        '201':
          description: 등록 성공
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RegisterDeviceResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          description: 이미 등록된 기기 또는 활성 키 충돌
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/devices/revoke:
    post:
      tags: [Device]
      summary: 기기 또는 키 폐기
      operationId: revokeDevice
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RevokeDeviceRequest'
      responses:
        '200':
          description: 폐기 성공
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RevokeDeviceResponse'
        '404':
          $ref: '#/components/responses/NotFound'

  /api/collateral:
    post:
      tags: [Collateral]
      summary: 담보 잠금 및 오프라인 한도 발급
      operationId: createCollateral
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateCollateralRequest'
      responses:
        '201':
          description: 담보 생성 성공
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CreateCollateralResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          description: 활성 담보 정책 충돌
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/collateral/{collateralId}:
    get:
      tags: [Collateral]
      summary: 담보 상세 조회
      operationId: getCollateral
      parameters:
        - in: path
          name: collateralId
          required: true
          schema:
            type: string
      responses:
        '200':
          description: 담보 조회 성공
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CollateralDetailResponse'
        '404':
          $ref: '#/components/responses/NotFound'

  /api/settlements:
    post:
      tags: [Settlement]
      summary: proof 배치 업로드 및 정산 요청 생성
      operationId: createSettlementBatch
      parameters:
        - in: header
          name: Idempotency-Key
          required: true
          schema:
            type: string
            maxLength: 64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateSettlementBatchRequest'
      responses:
        '202':
          description: Idempotency-Key 기준으로 업로드를 수락하고 비동기 정산 saga를 접수
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SettlementBatchDetailResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          description: 동일 idempotency key 중복 요청
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/settlements/{batchId}:
    get:
      tags: [Settlement]
      summary: 정산 배치 상태 조회
      description: |
        배치 row뿐 아니라 배치에 속한 settlement request의 saga/reconciliation 상태를 함께 조회한다.
        ledger/history projection 동기화가 재시도 중이면 `settlementWorkflowStage=RETRYABLE_FAILED`,
        dead-letter 또는 서명/증명 검증 같은 non-retryable 실패이면 `DEAD_LETTERED`를 반환한다.
      operationId: getSettlementBatch
      parameters:
        - in: path
          name: batchId
          required: true
          schema:
            type: string
      responses:
        '200':
          description: 배치 상태 조회 성공
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SettlementBatchDetailResponse'
        '404':
          $ref: '#/components/responses/NotFound'

  /api/settlements/{settlementId}/finalize:
    post:
      tags: [Settlement]
      summary: 수동/관리자 재처리 또는 finalize
      operationId: finalizeSettlement
      parameters:
        - in: path
          name: settlementId
          required: true
          schema:
            type: string
      responses:
        '200':
          description: finalize 성공
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FinalizeSettlementResponse'
        '404':
          $ref: '#/components/responses/NotFound'

  /api/ledger/history:
    get:
      tags: [Settlement]
      summary: 오프라인페이 송신/수취 거래 히스토리 조회
      description: |
        수취 거래 row는 거래별 `unsettledAmount`, `settledAmount`를 포함한다.
        `unsettledAmount`는 수취 완료 후 아직 담보금에 반영되지 않은 금액이고,
        수동 정산 또는 자동정산 topup 완료 시 `settledAmount`로 이동한다.
      operationId: getOfflinePayLedgerHistory
      parameters:
        - in: query
          name: userId
          required: true
          schema:
            type: integer
            format: int64
        - in: query
          name: assetCode
          required: false
          schema:
            type: string
            default: KORI
        - in: query
          name: size
          required: false
          schema:
            type: integer
            minimum: 1
            maximum: 30
            default: 30
        - in: query
          name: page
          required: false
          schema:
            type: integer
            minimum: 0
            default: 0
      responses:
        '200':
          description: 오프라인페이 ledger history
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LedgerHistoryResponse'

  /api/admin/conflicts:
    get:
      tags: [Admin]
      summary: 충돌 로그 목록 조회
      operationId: getConflicts
      parameters:
        - in: query
          name: status
          schema:
            type: string
            enum: [OPEN, REVIEWING, RESOLVED]
        - in: query
          name: conflictType
          schema:
            type: string
            enum: [CHAIN_FORK, DUPLICATE_NONCE, DUPLICATE_COUNTER, EXPIRED, POLICY_VIOLATION]
        - in: query
          name: collateralId
          schema:
            type: string
        - in: query
          name: deviceId
          schema:
            type: string
        - in: query
          name: cursor
          schema:
            type: string
        - in: query
          name: size
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
      responses:
        '200':
          description: 충돌 목록 조회 성공
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ConflictListResponse'

  /api/admin/ops/dead-letters:
    get:
      tags: [Admin]
      summary: dead-letter 배치 목록 조회
      operationId: getDeadLetters
      parameters:
        - in: query
          name: size
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
      responses:
        '200':
          description: dead-letter 목록 조회 성공

  /api/admin/ops/dead-letters/{batchId}/retry:
    post:
      tags: [Admin]
      summary: dead-letter 배치 수동 재처리 enqueue
      operationId: retryDeadLetterBatch
      parameters:
        - in: path
          name: batchId
          required: true
          schema:
            type: string
      responses:
        '200':
          description: 재처리 enqueue 성공
        '404':
          $ref: '#/components/responses/NotFound'

  /api/admin/ops/proofs:
    get:
      tags: [Admin]
      summary: proof lifecycle 최근 목록 조회
      operationId: getOfflineProofs
      parameters:
        - in: query
          name: size
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
        - in: query
          name: status
          schema:
            type: string
            enum: [UPLOADED, VALIDATING, VERIFIED_OFFLINE, SETTLED, REJECTED, CONFLICTED, EXPIRED, FAILED]
        - in: query
          name: channelType
          schema:
            type: string
            enum: [NFC, QR, BLE, UNKNOWN]
      responses:
        '200':
          description: proof 목록 조회 성공

  /api/admin/ops/proofs/overview:
    get:
      tags: [Admin]
      summary: proof lifecycle 상태 요약 조회
      operationId: getOfflineProofOverview
      parameters:
        - in: query
          name: size
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
        - in: query
          name: channelType
          schema:
            type: string
            enum: [NFC, QR, BLE, UNKNOWN]
      responses:
        '200':
          description: proof overview 조회 성공

  /api/admin/metrics/settlements/timeseries:
    get:
      tags: [Admin]
      summary: settlement 배치 상태 및 conflict 시계열 조회
      operationId: getSettlementTimeseries
      parameters:
        - in: query
          name: hours
          schema:
            type: integer
            minimum: 1
            maximum: 720
            default: 24
      responses:
        '200':
          description: 시계열 조회 성공

components:
  schemas:
    CurrentSnapshotResponse:
      type: object
      required: [userId, deviceId, assetCode, deviceRegistration, walletRefreshRequired, refreshedAt, staleAfterMs]
      properties:
        userId:
          type: integer
          format: int64
        deviceId:
          type: string
        assetCode:
          type: string
        collateral:
          nullable: true
          $ref: '#/components/schemas/CollateralSnapshot'
        walletRefreshRequired:
          type: boolean
        refreshedAt:
          type: string
          format: date-time
        staleAfterMs:
          type: integer
          format: int64

    CollateralSnapshot:
      type: object
      required:
        - collateralId
        - userId
        - deviceId
        - assetCode
        - lockedAmount
        - remainingAmount
        - collateralTotal
        - unsettledOutgoing
        - availableForPay
        - policyVersion
        - status
        - snapshotVersion
      properties:
        collateralId:
          type: string
        userId:
          type: integer
          format: int64
        deviceId:
          type: string
        assetCode:
          type: string
        lockedAmount:
          type: string
          description: Backward-compatible collateral principal field.
        remainingAmount:
          type: string
          description: Backward-compatible server collateral remaining snapshot. Do not use as transaction-settled wallet balance.
        collateralTotal:
          type: string
          description: Current server-side online collateral amount after finalized topup, release, and offline payment deductions.
        unsettledOutgoing:
          type: string
          description: Outgoing amount not yet reflected to server collateral. Normally zero after server settlement finalizes deductions.
        availableForPay:
          type: string
          description: Current server-side amount available for online/offline snapshot sync.
        policyVersion:
          type: integer
        status:
          type: string
        initialStateRoot:
          type: string
        externalLockId:
          type: string
        expiresAt:
          type: string
        updatedAt:
          type: string
        snapshotVersion:
          type: integer
          format: int64

    LedgerHistoryResponse:
      type: object
      required: [assetCode, sentItems, receivedItems, totalReceivedAmount, refreshedAt]
      properties:
        assetCode:
          type: string
        sentItems:
          type: array
          items:
            $ref: '#/components/schemas/LedgerHistoryItem'
        receivedItems:
          type: array
          items:
            $ref: '#/components/schemas/LedgerHistoryItem'
        totalReceivedAmount:
          type: string
        refreshedAt:
          type: string
          format: date-time
        page:
          type: integer
          minimum: 0
        size:
          type: integer
          minimum: 1
          maximum: 30
        sentHasNext:
          type: boolean
        receivedHasNext:
          type: boolean

    LedgerHistoryItem:
      type: object
      required: [id, amount, balance, statusCode, transactionType, unsettledAmount, settledAmount]
      properties:
        id:
          type: string
        date:
          type: string
        title:
          type: string
        memo:
          type: string
        amount:
          type: string
        subAmount:
          type: string
          nullable: true
        balance:
          type: string
        status:
          type: string
        statusCode:
          type: string
        network:
          type: string
        token:
          type: string
        walletAddress:
          type: string
        time:
          type: string
          format: date-time
        transactionType:
          type: string
        transactionScope:
          type: string
        detailType:
          type: string
        counterpartyName:
          type: string
        fee:
          type: string
        category:
          type: string
        paymentMethod:
          type: string
        unsettledAmount:
          type: string
          description: 거래별 수취 정산대기 금액. 담보 반영 전에는 수취 금액, 반영 후에는 0.
        settledAmount:
          type: string
          description: 거래별 담보 반영 완료 금액. 추후 레퍼럴 정산 기준으로 사용할 수 있는 확정 금액.

    RegisterDeviceRequest:
      type: object
      required: [userId, deviceId, publicKey, keyVersion, platform]
      properties:
        userId:
          type: integer
          format: int64
        deviceId:
          type: string
          maxLength: 64
        publicKey:
          type: string
          description: PEM 또는 base64 인코딩 공개키
        keyVersion:
          type: integer
          minimum: 1
        platform:
          type: string
          enum: [ANDROID, IOS]
        deviceModel:
          type: string
        osVersion:
          type: string
        appVersion:
          type: string

    RegisterDeviceResponse:
      type: object
      required: [deviceId, userId, keyVersion, status]
      properties:
        deviceId:
          type: string
        userId:
          type: integer
          format: int64
        keyVersion:
          type: integer
        status:
          type: string
          enum: [ACTIVE]

    RevokeDeviceRequest:
      type: object
      required: [deviceId]
      properties:
        deviceId:
          type: string
        keyVersion:
          type: integer
        reason:
          type: string
          maxLength: 255

    RevokeDeviceResponse:
      type: object
      required: [deviceId, status]
      properties:
        deviceId:
          type: string
        status:
          type: string
          enum: [REVOKED]

    CreateCollateralRequest:
      type: object
      required: [userId, deviceId, assetCode, amount, initialStateRoot, policyVersion]
      properties:
        userId:
          type: integer
          format: int64
        deviceId:
          type: string
        assetCode:
          type: string
          example: USDT
        amount:
          type: string
          pattern: '^\d+(\.\d{1,18})?$'
        initialStateRoot:
          type: string
          maxLength: 128
        policyVersion:
          type: integer
          minimum: 1
        expiresAt:
          type: string
          format: date-time

    CreateCollateralResponse:
      type: object
      required: [collateralId, userId, deviceId, lockedAmount, remainingAmount, initialStateRoot, policyVersion, status]
      properties:
        collateralId:
          type: string
        userId:
          type: integer
          format: int64
        deviceId:
          type: string
        lockedAmount:
          type: string
        remainingAmount:
          type: string
        initialStateRoot:
          type: string
        policyVersion:
          type: integer
        status:
          type: string
          enum: [ACTIVE, FROZEN, CLOSED]
        expiresAt:
          type: string
          format: date-time

    CollateralDetailResponse:
      allOf:
        - $ref: '#/components/schemas/CreateCollateralResponse'

    VoucherProof:
      type: object
      required:
        - voucherId
        - collateralId
        - issuerDeviceId
        - receiverDeviceId
        - keyVersion
        - policyVersion
        - prevHash
        - newHash
        - amount
        - counter
        - nonce
        - timestamp
        - expiresAt
        - signature
        - payload
      properties:
        voucherId:
          type: string
          maxLength: 64
        collateralId:
          type: string
          maxLength: 64
        issuerDeviceId:
          type: string
          maxLength: 64
        receiverDeviceId:
          type: string
          maxLength: 64
        keyVersion:
          type: integer
          minimum: 1
        policyVersion:
          type: integer
          minimum: 1
        prevHash:
          type: string
          maxLength: 128
        newHash:
          type: string
          maxLength: 128
        amount:
          type: string
          pattern: '^\d+(\.\d{1,18})?$'
        counter:
          type: integer
          format: int64
          minimum: 1
        nonce:
          type: string
          maxLength: 128
        timestamp:
          type: integer
          format: int64
        expiresAt:
          type: integer
          format: int64
        signature:
          type: string
          description: base64 encoded signature
        canonicalPayload:
          type: string
          description: optional canonical queue envelope. QR offline sends should sign this envelope when a dedicated sender envelope signature is available.
        payload:
          type: object
          required: [paymentFlow]
          additionalProperties: true
          properties:
            paymentFlow:
              type: string
              enum: [FAST_PAYMENT, MANUAL_PAYMENT, QR_OFFLINE_PAYMENT]
            paymentMethod:
              type: string
              enum: [BLE, NFC, QR]
            connectionType:
              type: string
              enum: [FAST_CONTACT, MANUAL_SELECTION, QR_OFFLINE]

    CreateSettlementBatchRequest:
      type: object
      required: [uploaderType, uploaderDeviceId, proofs]
      properties:
        uploaderType:
          type: string
          enum: [SENDER, RECEIVER]
        uploaderDeviceId:
          type: string
        triggerMode:
          type: string
          default: MANUAL
          enum: [MANUAL, AUTO, QR_OFFLINE_SYNC]
        proofs:
          type: array
          minItems: 1
          maxItems: 500
          items:
            $ref: '#/components/schemas/VoucherProof'

    SettlementBatchDetailResponse:
      type: object
      required:
        - batchId
        - status
        - proofsCount
        - triggerMode
        - requestIds
        - idempotencyKey
        - acceptedCount
        - asyncProcessing
        - serverWorkflowStage
      properties:
        batchId:
          type: string
        status:
          type: string
          enum: [CREATED, UPLOADED, VALIDATING, PARTIALLY_SETTLED, SETTLED, FAILED, CLOSED]
        proofsCount:
          type: integer
        triggerMode:
          type: string
          enum: [MANUAL, AUTO, QR_OFFLINE_SYNC]
        requestIds:
          type: array
          items:
            type: string
        idempotencyKey:
          type: string
          description: Idempotency-Key header accepted for this batch. Replaying the same key returns the existing batch instead of creating duplicate settlement requests.
        acceptedCount:
          type: integer
          description: Number of settlement requests accepted into the async saga.
        asyncProcessing:
          type: boolean
          description: true when the batch was accepted for worker/saga processing rather than finalized synchronously.
        serverWorkflowStage:
          type: string
          enum: [SERVER_ACCEPTING, SERVER_ACCEPTED]
        settlementWorkflowStage:
          type: string
          nullable: true
          description: |
            Async settlement saga stage. `SETTLEMENT_ACCEPTED`는 서버가 요청을 접수한 상태,
            `LEDGER_SYNCED`는 coin_manage 정산 및 후속 history/projection 동기화가 진행/완료된 상태,
            `RETRYABLE_FAILED`는 재시도 가능한 외부 연동 실패,
            `DEAD_LETTERED`는 dead-letter 또는 non-retryable 검증 실패를 의미한다.
          enum: [SETTLEMENT_ACCEPTED, LEDGER_SYNCED, RETRYABLE_FAILED, DEAD_LETTERED]

    SettlementProofResult:
      type: object
      required: [voucherId, settlementStatus]
      properties:
        voucherId:
          type: string
        settlementStatus:
          type: string
          enum: [SETTLED, REJECTED, CONFLICTED, EXPIRED, REFUNDED]
        reasonCode:
          type: string
        detail:
          type: string

    FinalizeSettlementResponse:
      type: object
      required: [settlementId, status]
      properties:
        settlementId:
          type: string
        status:
          type: string
          enum: [SETTLED, REJECTED, CONFLICTED, EXPIRED, REFUNDED]

    ConflictListResponse:
      type: object
      required: [items]
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/ConflictItem'
        nextCursor:
          type: string
          nullable: true

    ConflictItem:
      type: object
      required: [conflictId, conflictType, severity, status, collateralId, voucherId]
      properties:
        conflictId:
          type: string
        conflictType:
          type: string
        severity:
          type: string
          enum: [LOW, MEDIUM, HIGH, CRITICAL]
        status:
          type: string
          enum: [OPEN, REVIEWING, RESOLVED]
        collateralId:
          type: string
        voucherId:
          type: string
        deviceId:
          type: string
        reason:
          type: string
        createdAt:
          type: string
          format: date-time

    ErrorResponse:
      type: object
      required: [code, message]
      properties:
        code:
          type: string
        message:
          type: string
        detail:
          type: object
          additionalProperties: true

  responses:
    BadRequest:
      description: 잘못된 요청
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
    NotFound:
      description: 리소스를 찾을 수 없음
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
1-2. API 설계에서 꼭 반영할 규칙

이 부분은 구현 사고 방지용이다.

정산 업로드

Idempotency-Key는 필수

같은 키로 같은 요청이면 기존 batchId 재반환

같은 키로 다른 payload면 409

proof 검증 규칙

요청 수락 시점에는 schema + 최소 형식 검증

실제 충돌/체인/정책 검증은 비동기 worker

finalize 엔드포인트

PoC에서는 수동 재처리 용도

이후엔 관리자 전용으로 제한하는 게 맞음

2. 핵심 도메인 로직 먼저 구현

네 문서 기준으로 서버 핵심은 사실상 아래 5개다.

ProofSchemaValidator

PublicKeyResolver

ProofChainValidator

ConflictDetector

SettlementPolicyEngine

여기서 우선순위를 잘못 잡으면 구현이 무조건 꼬인다.

2-1. 구현 우선순위
1순위: ProofSchemaValidator

이유:

모든 입력의 진입점

잘못된 payload를 초기에 컷 가능

검증 항목:

필수 필드 존재

문자열 길이

amount > 0

counter >= 1

keyVersion >= 1

policyVersion >= 1

timestamp / expiresAt 역전 금지

prevHash/newHash 포맷

signature base64 포맷

2순위: SignatureVerifier + PublicKeyResolver

이유:

기기 등록 구조와 직접 연결

오프라인 proof 신뢰성의 최소 단위

검증 항목:

issuerDeviceId + keyVersion 조합으로 공개키 조회

canonical payload 생성

signature verify

3순위: ConflictDetector

이유:

double spend 핵심

검출 항목:

동일 voucherId

동일 (issuerDeviceId, nonce)

동일 (collateralId, counter)인데 newHash가 다름

동일 counter에 대해 receiver가 다름

4순위: ProofChainValidator

이유:

체인 불연속/분기 탐지

검증 항목:

prevHash -> newHash 연결

counter strictly increasing

collateral의 genesis root와 첫 proof 연결

같은 collateral 내 proof 정렬 후 연속성 보장

5순위: SettlementPolicyEngine

이유:

서버 정책 판정 중앙화

판정 항목:

담보 active 여부

remaining amount 초과 여부

expiresAt 초과 여부

revoked/frozen device 여부

policyVersion mismatch 대응

2-2. 권장 처리 순서

정산 로직은 아래 순서를 고정하는 게 좋다.

1. schema validate
2. load collateral/device/key context
3. verify signature
4. detect duplicate / replay / conflict
5. validate chain continuity
6. evaluate policy
7. persist settlement result
8. emit conflict/admin event if needed

이 순서를 흐트러뜨리면, 예를 들어 체인 검증 전에 공개키 조회가 누락되거나, conflict 전에 remaining amount를 차감하는 사고가 난다.
