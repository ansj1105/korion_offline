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
  - name: Time
  - name: ClientTrace
  - name: Admin

paths:
  /api/time/sync:
    get:
      tags: [Time]
      summary: Offline Pay 서버 시간 동기화 anchor 조회
      description: |
        앱은 Offline Pay 거래/evidence 시간 계산용으로만 이 값을 저장한다.
        전역 기기 시간이나 UI 타이머 기준을 대체하지 않는다.
      operationId: syncOfflinePayServerTime
      parameters:
        - in: header
          name: X-Client-Time-Zone
          required: false
          schema:
            type: string
          description: 앱이 감지한 IANA timezone.
        - in: header
          name: X-Client-Time-Zone-Offset-Minutes
          required: false
          schema:
            type: integer
          description: 앱이 감지한 timezone offset minutes.
      responses:
        '200':
          description: 서버 시간 anchor
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TimeSyncResponse'

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

  /api/settlements/local-evidence:
    post:
      tags: [Settlement]
      summary: 로컬 offline-pay evidence 직접 업로드
      description: |
        `offline_pay_local_blocks_v1`처럼 앱 로컬 append-only block store에 남은 sender/receiver evidence를 서버 감사 테이블에 직접 적재한다.
        이 endpoint는 proof 또는 settlement를 생성하지 않으며, receiver-only evidence만으로 정산을 확정하지 않는다.
        서버는 canonicalPayload SHA-256, 등록 보안기기 서명, voucher/device/amount/counter/nonce 필드 일치를 검증한다.
        검증된 evidence만 `stored`로 집계되고, 검증 실패 evidence는 final settlement gate를 열지 않는다.
        최종 담보 반영과 수신자 정산은 `/api/settlements` sender proof와 matching verified receiver evidence가 모두 확인된 뒤 별도 settlement flow가 처리한다.
      operationId: ingestLocalEvidence
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
              $ref: '#/components/schemas/SubmitLocalEvidenceRequest'
      responses:
        '202':
          description: 저장 가능한 local evidence를 `offline_pay_local_evidence`에 upsert
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LocalEvidenceIngestResult'
        '400':
          $ref: '#/components/responses/BadRequest'

  /api/settlements/local-evidence/reconcile:
    post:
      tags: [Settlement]
      summary: direct local evidence 기반 settlement carrier 생성
      description: |
        `offline_pay_local_evidence`에 direct upload로 저장된 VERIFIED sender evidence와 matching VERIFIED receiver evidence를 조회한다.
        기존 `offline_payment_proofs`가 없는 voucher에 한해 서버 내부에서 `/api/settlements`와 동일한 proof/settlement carrier를 생성한다.
        receiver-only evidence는 이 endpoint에서도 정산을 만들 수 없으며, sender evidence에 proof 생성 필수 필드(collateralId, keyVersion, policyVersion, timestampMs, expiresAtMs)가 없으면 skip된다.
      operationId: reconcileDirectLocalEvidence
      parameters:
        - in: query
          name: limit
          required: false
          schema:
            type: integer
            default: 50
            minimum: 1
            maximum: 500
      responses:
        '202':
          description: direct local evidence pair를 settlement carrier로 승격
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/DirectLocalEvidenceReconcileResult'

  /api/settlements/local-evidence/status:
    get:
      tags: [Settlement]
      summary: 로컬 evidence 매칭 상태 조회
      description: |
        `voucherId` 또는 `sessionId` 기준으로 direct local evidence 저장/매칭 상태를 조회한다.
        두 값을 모두 생략하면 운영 화면용 전역 aggregate를 반환한다.
        운영 화면은 이 응답의 `matched`, `awaitingCarrier`, `staleAwaitingCarrier`, `failed`를 이용해 carrier 대기 또는 매칭 완료 상태를 표시한다.
      operationId: getLocalEvidenceStatus
      parameters:
        - in: query
          name: voucherId
          required: false
          schema:
            type: string
            maxLength: 64
        - in: query
          name: sessionId
          required: false
          schema:
            type: string
            maxLength: 160
        - in: query
          name: staleAfterHours
          required: false
          schema:
            type: integer
            default: 24
            minimum: 1
          description: awaitingCarrier 중 stale로 표시할 기준 시간. 시간만으로 실패 처리하지 않고 운영 추적 지표로만 사용한다.
      responses:
        '200':
          description: local evidence status summary
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LocalEvidenceStatusResponse'
        '400':
          $ref: '#/components/responses/BadRequest'

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
    TimeSyncResponse:
      type: object
      required: [serverTime, serverEpochMs, serverTimeZone, clientTimeZone, clientTimeZoneOffsetMinutes]
      properties:
        serverTime:
          type: string
          format: date-time
          description: UTC 기준 서버 시간. Offline Pay estimated server time anchor로 저장된다.
        serverEpochMs:
          type: integer
          format: int64
          description: UTC 기준 서버 epoch milliseconds.
        serverTimeZone:
          type: string
          description: 서버 검증 기준 timezone. 기본값 UTC.
        clientTimeZone:
          type: string
          description: 클라이언트가 보낸 IANA timezone 또는 UTC fallback.
        clientTimeZoneOffsetMinutes:
          type: integer
          description: 클라이언트가 보낸 timezone offset minutes 또는 0 fallback.

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
        serverTimeZone:
          type: string
          description: Server canonical timezone for verification timestamps. Defaults to UTC.
        clientTimeZone:
          type: string
          description: IANA timezone requested by the client via X-Client-Time-Zone, or UTC fallback.
        clientTimeZoneOffsetMinutes:
          type: integer
          description: Client timezone offset minutes reported by X-Client-Time-Zone-Offset-Minutes.

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
          enum: [PENDING, CONFIRMED, SETTLED, FAILED, EXPIRED, REJECTED, LOCKED]
          description: |
            Public ledger status normalized for client display.
            PENDING=local protocol evidence exists, but server validation is not complete.
            CONFIRMED=server validation completed but receiver wallet/history settlement is not finalized.
            SETTLED=receiver wallet/history settlement completed.
            FAILED=validation, transport, or settlement failure.
            EXPIRED=offline transaction proof or policy validity window expired.
            REJECTED=policy rejection, not a technical validation failure.
            LOCKED=anomaly or security-review lock.
            Legacy COMPLETED may appear in older client caches only; new API responses must use CONFIRMED. Final completion is SETTLED, not COMPLETED or CONFIRMED.
            Internal statuses such as VALIDATING, CONSUMED_PENDING_SETTLEMENT, REJECTED, and EXPIRED may remain in domain tables and traces, but must be projected to this public enum before returning ledger/history rows.
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
          description: |
            Offline sender envelope. Legacy payloads without hybrid time fields remain accepted, but any payload that includes one hybrid time field must include the full hybrid time/sequence envelope in both `payload` and `canonicalPayload`.
            Server validation treats `offlineTxSequence` as the sender-device ordering key and rejects duplicate or inconsistent sequence submissions.
            When `senderLocalBlock=true` or `receiverLocalBlock=true`, the corresponding `*VoucherId`, `*Amount`, `*SenderDeviceId`, `*ReceiverDeviceId`, `*Counter`, `*PrevHash`, `*NewHash`, `*Nonce`, and `*Signature` fields must match the top-level proof fields. Receiver uploads may include `receiverLocalBlock*` fields together with the sender-signed payload so the server can cross-check sender and receiver local evidence by session/voucher/hash/signature before accepting confirmation.
            Receiver uploads must include `receiverSettlementMode` (`AUTO` or `MANUAL`) and `receiverSettlementAutoEnabled`. A validated manual receiver upload keeps `receivedUnsettledAmount` until `/api/settlements/received/confirm` is called; only automatic receiver uploads or explicit confirmation may create receiver wallet/history settlement.
            Receiver-only local evidence is not a settlement proof. A receiver upload must match an existing sender proof for the same voucher/session; otherwise the server rejects the upload before creating proof or settlement rows.
            Sender local evidence alone is not enough for final collateral settlement. If a sender proof carries `senderLocalBlock=true`, the server keeps the settlement `PENDING` with `RECEIVER_EVIDENCE_REQUIRED` until a matching receiver local block confirms the same voucher, devices, counter, hash, nonce, signature, and amount.
            Sender and receiver local evidence is stored in `offline_pay_local_evidence` for audit and replay recovery. New receiver uploads should include `receiverEvidenceBlock=true`, `receiverEvidenceBlockCanonicalPayload`, `receiverEvidenceBlockNewHash`, `receiverEvidenceBlockSignature`, and `receiverEvidenceBlockSenderProof*` reference fields. The server recomputes SHA-256 over the canonical payload, verifies the receiver device signature, and checks the sender proof reference before final settlement.
          properties:
            requestId:
              type: string
              description: Concrete offline payment request/session id. When present it must match `txId`.
            txId:
              type: string
              description: Unique offline transaction id signed by the sender device.
            offlineTxSequence:
              type: integer
              format: int64
              minimum: 1
              description: Monotonic per-sender-device offline transaction sequence. Server uses this as the primary deterministic order key when offline estimated times collide and rejects duplicate sender-device sequence submissions.
            deviceTime:
              type: string
              format: date-time
              description: Device wall-clock timestamp captured for diagnostics only. It is not trusted as the canonical offline order source.
            lastServerSyncTime:
              type: string
              format: date-time
              description: Last server time cached while the device was online.
            estimatedServerTime:
              type: string
              format: date-time
              description: Calculated offline server-time estimate, equal to `lastServerSyncTime + elapsedTimeMs` within server tolerance.
            elapsedTimeMs:
              type: integer
              format: int64
              minimum: 0
              description: Monotonic elapsed milliseconds since `lastServerSyncTime`.
            elapsedTimeSource:
              type: string
              description: Client monotonic clock source used to derive `elapsedTimeMs`.
            serverSyncAgeMs:
              type: integer
              format: int64
              minimum: 0
              description: Diagnostic age of the cached server sync at payload creation time.
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
          enum: [MANUAL, AUTO, BLE_OFFLINE_SYNC, NFC_OFFLINE_SYNC, QR_OFFLINE_SYNC]
        proofs:
          type: array
          minItems: 1
          maxItems: 500
          items:
            $ref: '#/components/schemas/VoucherProof'

    SubmitLocalEvidenceRequest:
      type: object
      required: [uploaderType, uploaderDeviceId, evidences]
      properties:
        uploaderType:
          type: string
          enum: [SENDER, RECEIVER]
        uploaderDeviceId:
          type: string
          maxLength: 64
        evidences:
          type: array
          minItems: 1
          items:
            $ref: '#/components/schemas/LocalEvidence'

    LocalEvidence:
      type: object
      required:
        - voucherId
        - direction
        - senderDeviceId
        - receiverDeviceId
        - amount
        - counter
        - newHash
        - nonce
        - signature
        - canonicalPayload
        - keyId
        - publicKeyFingerprint
        - transportSessionHash
        - transportTranscriptSource
        - payload
      properties:
        voucherId:
          type: string
          maxLength: 64
        sessionId:
          type: string
          maxLength: 64
        direction:
          type: string
          enum: [SEND, RECEIVE]
        senderDeviceId:
          type: string
          maxLength: 64
        receiverDeviceId:
          type: string
          maxLength: 64
        amount:
          type: string
          pattern: '^\d+(\.\d{1,18})?$'
        counter:
          type: integer
          format: int64
          minimum: 1
        prevHash:
          type: string
          maxLength: 128
        newHash:
          type: string
          maxLength: 128
        nonce:
          type: string
          maxLength: 128
        signature:
          type: string
        canonicalPayload:
          type: string
          description: Hash/signature 대상인 local block canonical payload.
        merchantId:
          type: string
          maxLength: 64
          description: Optional merchant id for store/payment reporting.
        partnerId:
          type: string
          maxLength: 64
          description: Optional sales/merchant partner id.
        leaderId:
          type: string
          maxLength: 64
          description: Optional country leader id for partner settlement reporting.
        countryCode:
          type: string
          maxLength: 2
          description: Optional ISO country code. Server stores uppercase when provided.
        storeId:
          type: string
          maxLength: 64
          description: Optional store id.
        orderId:
          type: string
          maxLength: 128
          description: Optional merchant order id.
        paymentIntentId:
          type: string
          maxLength: 128
          description: Optional payment intent/request id.
        invoiceId:
          type: string
          maxLength: 128
          description: Optional invoice or payment document id.
        fiatAmount:
          type: string
          pattern: '^\d+(\.\d{1,18})?$'
          description: Optional local fiat amount used for reporting.
        fiatCurrency:
          type: string
          maxLength: 8
          description: Optional local fiat currency. Server stores uppercase when provided.
        exchangeRate:
          type: string
          pattern: '^\d+(\.\d{1,18})?$'
          description: Optional KORI conversion rate applied to the transaction.
        rateTimestamp:
          type: string
          format: date-time
          description: Optional rate timestamp. This is reporting metadata, not the settlement clock.
        schemaVersion:
          type: string
          maxLength: 32
          description: Optional local block schema version for audit and migration.
        protocolVersion:
          type: string
          maxLength: 32
          description: Optional offline payment protocol version.
        hashAlgorithm:
          type: string
          maxLength: 64
          description: Optional local block hash algorithm, for example SHA-256.
        signatureAlgorithm:
          type: string
          maxLength: 64
          description: Optional local evidence signature algorithm, for example SHA256withECDSA or Ed25519.
        keyId:
          type: string
          maxLength: 128
          description: Required signing key id. Must match the registered device key id format `device:{deviceId}:v{keyVersion}`.
        publicKeyFingerprint:
          type: string
          maxLength: 256
          description: Required SHA-256 fingerprint of the registered device public key. Server rejects local evidence when this does not match the active device registration.
        appVersion:
          type: string
          maxLength: 64
          description: Optional app version that created the local evidence.
        deviceAttestationId:
          type: string
          maxLength: 256
          description: Required server-verified device attestation/integrity evidence id. Must match the signed canonicalPayload and registered device trust metadata.
        deviceAttestationVerdict:
          type: string
          enum: [HARDWARE_BACKED_VERIFIED]
          description: Required server-verified attestation verdict for offline-pay local evidence upload.
        serverVerifiedTrustLevel:
          type: string
          enum: [SERVER_VERIFIED]
          description: Required trust level granted after server-side attestation verification.
        serverAttestationVerifiedAt:
          type: string
          format: date-time
          description: Required server verification timestamp for the attestation verdict. This is trace/freshness metadata; settlement time still uses server-side processing time.
        transportSessionHash:
          type: string
          pattern: '^[0-9a-fA-F]{64}$'
          description: Required SHA-256 hash produced by the native BLE/NFC/QR transport transcript producer. This must also be included in the signed canonicalPayload and is not the settlement clock. App-level correlation hashes are not accepted as local evidence. If `transportTranscript` is supplied, the server recomputes SHA-256 over the transcript bytes and requires it to match this value.
        transportTranscriptSource:
          type: string
          enum:
            - NATIVE_BLE_SEND_TRANSCRIPT_V1
            - NATIVE_BLE_RECEIVE_TRANSCRIPT_V1
            - NATIVE_NFC_BRIDGE_TRANSCRIPT_V1
            - NATIVE_QR_SCAN_TRANSCRIPT_V1
          description: Required native transport transcript producer id. Must match the signed canonicalPayload and one of the server-approved native transcript sources.
        transportTranscript:
          type: string
          maxLength: 16384
          description: Optional raw native BLE/NFC/QR transport transcript. New clients should include this so the server can recompute `transportSessionHash`; legacy hash-only clients remain accepted for compatibility.
        transportTranscriptEncoding:
          type: string
          enum: [UTF-8, BASE64]
          description: Encoding of `transportTranscript`. Defaults to UTF-8 when omitted.
        payload:
          type: object
          additionalProperties: true

    LocalEvidenceIngestResult:
      type: object
      required: [requested, stored, skipped, matched, awaitingCarrier]
      properties:
        requested:
          type: integer
          description: 요청에 포함된 evidence 수.
        stored:
          type: integer
          description: canonical hash, device signature, identity field 검증을 통과해 정산 매칭 후보로 저장된 evidence 수. 이 값은 proof 또는 settlement 생성 성공을 의미하지 않는다.
        skipped:
          type: integer
          description: 필수값 누락, uploader device 불일치, hash/signature/identity 검증 실패로 최종 정산 게이트를 열 수 없는 evidence 수.
        matched:
          type: integer
          description: 기존 `/api/settlements` sender proof carrier와 매칭되어 pending settlement wake-up 대상이 된 receiver evidence 수.
        awaitingCarrier:
          type: integer
          description: 검증/저장은 됐지만 아직 대응되는 settlement carrier가 없어 direct evidence store에 감사 증거로만 대기 중인 evidence 수.

    DirectLocalEvidenceReconcileResult:
      type: object
      required: [candidates, created, reused, finalized, rejected, skipped, batchIds, settlementIds]
      properties:
        candidates:
          type: integer
          description: matching receiver evidence가 있는 VERIFIED sender evidence 후보 수.
        created:
          type: integer
          description: proof/settlement carrier 생성이 접수된 후보 수.
        reused:
          type: integer
          description: 기존 `/api/settlements` carrier 또는 레이스로 먼저 생성된 proof/settlement를 재사용한 후보 수. 이 경우 direct evidence worker는 중복 proof를 만들지 않는다.
        finalized:
          type: integer
          description: direct evidence pair 또는 재사용 carrier 처리 결과 최종 SETTLED까지 진행된 settlement 수.
        rejected:
          type: integer
          description: direct evidence pair 또는 재사용 carrier 처리 결과 서버 검증에서 REJECTED/EXPIRED/CONFLICT로 닫힌 settlement 수.
        skipped:
          type: integer
          description: sender evidence에 proof 생성 필수 필드가 없거나 기존 proof에 settlement request가 없어 carrier 생성/재사용에서 제외된 후보 수.
        batchIds:
          type: array
          items:
            type: string
          description: direct evidence worker가 새로 생성한 settlement batch id 목록. 기존 carrier 재사용은 중복 batch를 만들지 않으므로 이 목록에 추가되지 않는다.
        settlementIds:
          type: array
          items:
            type: string
          description: direct evidence worker가 생성 또는 재사용 후 처리한 settlement request id 목록.

    LocalEvidenceStatusResponse:
      type: object
      required:
        - total
        - stored
        - matched
        - awaitingCarrier
        - failed
        - senderStored
        - receiverStored
        - senderMatched
        - receiverMatched
        - senderFailed
        - receiverFailed
        - state
        - staleAwaitingCarrier
        - staleAfterHours
      properties:
        voucherId:
          type: string
          nullable: true
          description: 조회된 evidence의 voucher id. 조회 결과가 없고 query에도 없으면 null.
        sessionId:
          type: string
          nullable: true
          description: 조회된 evidence의 session id. 조회 결과가 없고 query에도 없으면 null.
        total:
          type: integer
          description: 조건에 매칭된 evidence row 수.
        stored:
          type: integer
          description: verificationStatus=VERIFIED인 evidence 수.
        matched:
          type: integer
          description: VERIFIED이고 settlement proof/carrier와 matchedProofId로 연결된 evidence 수.
        awaitingCarrier:
          type: integer
          description: VERIFIED지만 아직 settlement carrier와 매칭되지 않은 evidence 수.
        failed:
          type: integer
          description: 검증 실패 또는 VERIFIED가 아닌 evidence 수.
        senderStored:
          type: integer
        receiverStored:
          type: integer
        senderMatched:
          type: integer
        receiverMatched:
          type: integer
        senderFailed:
          type: integer
        receiverFailed:
          type: integer
        state:
          type: string
          enum: [NOT_FOUND, AWAITING_CARRIER, MATCHED, FAILED, PARTIAL]
          description: 운영 화면 표시용 local evidence aggregate state.
        staleAwaitingCarrier:
          type: integer
          description: staleAfterHours보다 오래 carrier/proof 매칭을 기다린 VERIFIED evidence 수. 실패 처리 기준이 아니라 운영 추적 지표다.
        staleAfterHours:
          type: integer
          description: staleAwaitingCarrier 계산에 사용된 기준 시간.
        oldestAwaitingCarrierAt:
          type: string
          nullable: true
          description: 아직 carrier와 매칭되지 않은 VERIFIED evidence 중 가장 오래된 updated_at.
        latestUpdatedAt:
          type: string
          nullable: true
          description: 조건에 매칭된 evidence의 최신 updated_at.

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
          description: |
            Machine-readable diagnostic detail serialized as a JSON string when available.
            Trust contract fields such as `contractRequirements`, `requiredServerTrust`,
            `deviceTrustLevel`, `deviceAttestationVerdict`, `serverVerifiedTrustLevel`,
            `trustContractMet`, and `serverTrustVerified` are diagnostic at the current
            policy level and do not by themselves reject settlement.

    FinalizeSettlementResponse:
      type: object
      required: [settlementId, status]
      properties:
        settlementId:
          type: string
        status:
          type: string
          enum: [PENDING, SETTLED, FAILED]
          description: |
            Public settlement status projection. PENDING means server validation/finalization is still in progress,
            SETTLED means final settlement completed, and FAILED covers rejected, conflicted, or expired internal states.
            Internal saga statuses such as COMPLETED must not be returned through this response.

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
