# 모바일 + 서버 전체 시퀀스 다이어그램 + 클래스 설계

## 1. 전체 구성

### 주요 참여자
- Sender App
- Receiver App
- Secure Storage
- Offline Payment Engine
- Proof Signer
- Proof Verifier
- Server API
- Device Registry
- Collateral Service
- Settlement Engine
- Admin Console
- QR/BLE/NFC Channel

## 2. 시퀀스 다이어그램

### 2.1 기기 등록 + 공개키 등록

```mermaid
sequenceDiagram
    autonumber
    participant SA as Sender App
    participant SS as Secure Storage
    participant API as Server API
    participant DR as Device Registry

    SA->>SS: generateKeyPair()
    SS-->>SA: publicKey, keyRef

    SA->>API: POST /devices/register\n(deviceInfo, publicKey)
    API->>DR: registerDevice(deviceId, userId, publicKey, keyVersion)
    DR-->>API: registered
    API-->>SA: deviceId, keyVersion, status=ACTIVE
```

### 2.2 담보 잠금 + 오프라인 한도 발급

```mermaid
sequenceDiagram
    autonumber
    participant SA as Sender App
    participant API as Server API
    participant CS as Collateral Service
    participant DB as DB
    participant SS as Secure Storage

    SA->>API: POST /collaterals\n(userId, deviceId, amount)
    API->>CS: createCollateral(userId, deviceId, amount)
    CS->>DB: lockAsset(userId, amount)
    CS->>DB: createCollateralPool(deviceId, lockedAmount, initialStateRoot, policyVersion)
    DB-->>CS: collateralId, initialStateRoot
    CS-->>API: collateral metadata
    API-->>SA: collateralId, initialStateRoot, remainingLimit, policyVersion
    SA->>SS: saveLocalState(collateralId, initialStateRoot, counter=0, remainingAmount)
```

### 2.3 송신자 오프라인 바우처 생성

```mermaid
sequenceDiagram
    autonumber
    participant SA as Sender App
    participant SS as Secure Storage
    participant OPE as Offline Payment Engine
    participant PS as Proof Signer

    SA->>SS: loadLocalState(collateralId)
    SS-->>SA: previousStateHash, counter, remainingAmount, keyVersion, policyVersion

    SA->>OPE: createVoucher(amount)
    OPE->>OPE: validateRemainingAmount(amount)
    OPE->>OPE: nextCounter = counter + 1
    OPE->>OPE: nonce = generateNonce()
    OPE->>OPE: voucherId = generateVoucherId()
    OPE->>OPE: newHash = H(prevHash, amount, nextCounter, nonce, voucherId, keyVersion, policyVersion, timestamp)
    OPE->>PS: sign(canonicalPayload)
    PS-->>OPE: signature
    OPE->>SS: updateLocalState(newHash, nextCounter, remainingAmount - amount)
    OPE-->>SA: voucherProof
```

### 2.4 QR/BLE/NFC 전달 + 수신자 오프라인 검증

```mermaid
sequenceDiagram
    autonumber
    participant SA as Sender App
    participant CH as QR/BLE/NFC Channel
    participant RA as Receiver App
    participant RV as Proof Verifier
    participant RC as Receiver Cache

    SA->>CH: transfer(voucherProof)
    CH-->>RA: voucherProof

    RA->>RV: verifyFormat(voucherProof)
    RV->>RV: verifyCanonicalPayload()
    RV->>RV: verifySignature()
    RV->>RV: verifyExpiration()
    RV->>RC: checkDuplicate(voucherId, nonce, issuerDeviceId, counter)
    RC-->>RV: notDuplicated
    RV-->>RA: OFFLINE_VERIFIED

    RA->>RC: saveReceivedVoucher(status=RECEIVED_PENDING)
    RA-->>RA: showTemporaryReceivedAsset()
```

### 2.5 온라인 복귀 후 정산

```mermaid
sequenceDiagram
    autonumber
    participant SA as Sender App
    participant RA as Receiver App
    participant API as Server API
    participant SE as Settlement Engine
    participant DB as DB

    SA->>API: POST /settlements/upload\n(sender proofs batch, idempotencyKey)
    API->>SE: validateSenderBatch()

    RA->>API: POST /settlements/upload\n(receiver proofs batch, idempotencyKey)
    API->>SE: validateReceiverBatch()

    SE->>DB: loadCollateral(collateralId)
    SE->>DB: loadExistingProofs(voucherId, deviceId, counter, nonce)
    SE->>SE: verifyChainContinuity()
    SE->>SE: verifySignature()
    SE->>SE: verifyConflictRules()
    SE->>SE: calculateSettlementResult()

    alt valid settlement
        SE->>DB: markProofs(SETTLED)
        SE->>DB: deductCollateral()
        SE->>DB: transferAsset()
        DB-->>SE: success
        SE-->>API: SETTLED
        API-->>SA: settlement result
        API-->>RA: settlement result
    else conflict or reject
        SE->>DB: markProofs(CONFLICTED or REJECTED or EXPIRED)
        SE-->>API: conflict result
        API-->>SA: conflict result
        API-->>RA: conflict result
    end
```

### 2.6 키 폐기 / 기기 폐기

```mermaid
sequenceDiagram
    autonumber
    participant APP as Mobile App
    participant API as Server API
    participant DR as Device Registry
    participant CS as Collateral Service
    participant DB as DB

    APP->>API: POST /devices/revoke\n(deviceId or keyVersion)
    API->>DR: revokeDeviceOrKey()
    DR->>DB: update status = REVOKED
    DR-->>API: revoked

    API->>CS: freezeOpenCollateral(deviceId)
    CS->>DB: set collateral status = FROZEN
    CS-->>API: frozen
    API-->>APP: revoke completed
```

### 2.7 관리자 충돌 로그 조회

```mermaid
sequenceDiagram
    autonumber
    participant AD as Admin Console
    participant API as Server API
    participant SE as Settlement Engine
    participant DB as DB

    AD->>API: GET /admin/conflicts?status=CONFLICTED
    API->>SE: getConflictLogs()
    SE->>DB: queryConflictLogs()
    DB-->>SE: conflict details
    SE-->>API: conflict list
    API-->>AD: conflict list with reasons
```

## 3. 상태 전이 다이어그램

오프라인 바우처 상태는 모바일 로컬 상태와 서버 최종 상태를 구분한다.

- 모바일 송신자는 바우처를 생성하고 로컬 상태를 전이시킨다.
- 모바일 수신자는 바우처의 형식, 서명, 만료, 중복 수령 여부까지만 검증한다.
- 서버는 업로드된 proof를 기준으로 충돌, 체인 연속성, 서명 유효성, 만료, 환불, 폐기 정책을 반영하여 최종 상태를 판정한다.
- 따라서 `OFFLINE_VERIFIED`는 확정 자산 상태가 아니며, 최종 자산 확정 상태는 `SETTLED`만 인정한다.

### 3.1 송신 바우처 상태

```mermaid
stateDiagram-v2
    [*] --> DRAFT : createVoucher() 시작
    DRAFT --> ISSUED : proof 생성 + 서명 완료
    ISSUED --> SENT : QR/BLE/NFC 전송 성공
    ISSUED --> REJECTED : 로컬 검증 실패 / 생성 취소
    ISSUED --> EXPIRED : 만료 시간 초과

    SENT --> SETTLEMENT_PENDING : 서버 업로드 완료
    SENT --> EXPIRED : 정산 전 만료
    SENT --> CONFLICTED : 동일 collateral 체인 충돌 감지
    SENT --> REJECTED : 서버 스키마/서명/정책 검증 실패

    SETTLEMENT_PENDING --> SETTLED : 서버 정산 성공
    SETTLEMENT_PENDING --> CONFLICTED : 이중 사용 / counter 충돌 / nonce 충돌
    SETTLEMENT_PENDING --> REJECTED : 서명 불일치 / 체인 불연속 / 정책 위반
    SETTLEMENT_PENDING --> EXPIRED : 정산 시점 만료 판정
    SETTLEMENT_PENDING --> REFUNDED : 환불 승인

    REJECTED --> [*]
    CONFLICTED --> [*]
    EXPIRED --> [*]
    SETTLED --> [*]
    REFUNDED --> [*]
```

### 3.2 송신자 로컬 담보 상태

`VoucherStatus`와 `LocalCollateralState`는 같은 상태 머신이 아니다. 바우처 상태는 proof 단위 수명주기이고, 로컬 담보 상태는 송신자 단말이 보유한 collateral snapshot의 동기화 상태다.

```mermaid
stateDiagram-v2
    [*] --> SYNCED
    SYNCED --> UPDATED_LOCALLY : 새 voucher 발급
    UPDATED_LOCALLY --> SYNC_PENDING : 서버 미동기화 상태
    SYNC_PENDING --> SYNCED : 서버 동기화 완료
    SYNC_PENDING --> OUT_OF_SYNC : 서버 충돌 / 로컬 손상
    OUT_OF_SYNC --> REVOKED : 강제 폐기
    REVOKED --> [*]
```

### 3.3 수신 바우처 상태

```mermaid
stateDiagram-v2
    [*] --> RECEIVED_PENDING : voucherProof 수신
    RECEIVED_PENDING --> OFFLINE_VERIFIED : 형식/서명/만료/중복수신 검증 성공
    RECEIVED_PENDING --> REJECTED : 형식 오류 / 서명 검증 실패 / 중복 수신
    RECEIVED_PENDING --> EXPIRED : 수신 시점 만료

    OFFLINE_VERIFIED --> SETTLEMENT_PENDING : 서버 업로드 완료
    OFFLINE_VERIFIED --> EXPIRED : 서버 업로드 전 만료
    OFFLINE_VERIFIED --> REJECTED : 로컬 캐시 손상 / 업로드 불가

    SETTLEMENT_PENDING --> SETTLED : 서버 정산 성공
    SETTLEMENT_PENDING --> CONFLICTED : 동일 voucherId / counter / nonce 충돌
    SETTLEMENT_PENDING --> REJECTED : 서버 검증 실패
    SETTLEMENT_PENDING --> EXPIRED : 서버 만료 판정

    REJECTED --> [*]
    CONFLICTED --> [*]
    EXPIRED --> [*]
    SETTLED --> [*]
```

### 3.4 Device / Key 상태

기기 활성 상태와 키 수명주기는 바우처 상태와 별도로 관리해야 한다. 운영 디버깅 관점에서는 `device status`와 `key lifecycle`을 분리해서 보는 편이 낫다.

```mermaid
stateDiagram-v2
    [*] --> ACTIVE
    ACTIVE --> KEY_ROTATED : 키 교체
    ACTIVE --> FROZEN : 이상 징후 / 정책 위반
    KEY_ROTATED --> ACTIVE : 신규 키 활성화 완료
    FROZEN --> REVOKED : 강제 폐기
    ACTIVE --> REVOKED : 사용자 폐기 / 관리자 폐기
    REVOKED --> [*]
```

### 3.5 정산 배치 상태

proof 단건 상태와 별도로 업로드 배치 상태를 관리하면 운영에서 재시도, 부분 실패, 정산 집계를 다루기 쉬워진다.

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> UPLOADED : 업로드 성공
    UPLOADED --> VALIDATING : 서버 검증 중
    VALIDATING --> PARTIALLY_SETTLED : 일부 성공 / 일부 실패
    VALIDATING --> SETTLED : 전체 성공
    VALIDATING --> FAILED : 전체 실패
    PARTIALLY_SETTLED --> CLOSED
    SETTLED --> CLOSED
    FAILED --> CLOSED
    CLOSED --> [*]
```

## 4. 클래스 설계

### 4.1 공통 도메인 모델

```java
public class VoucherProof {
    private String voucherId;
    private String collateralId;
    private String issuerDeviceId;
    private int keyVersion;
    private int policyVersion;
    private String prevHash;
    private String newHash;
    private java.math.BigDecimal amount;
    private long counter;
    private String nonce;
    private long timestamp;
    private byte[] signature;
}

public class LocalCollateralState {
    private String deviceId;
    private String collateralId;
    private java.math.BigDecimal remainingAmount;
    private long counter;
    private String previousStateHash;
    private int keyVersion;
    private int policyVersion;
    private long lastSyncAt;
    private boolean revoked;
}

public enum LocalCollateralSyncStatus {
    SYNCED,
    UPDATED_LOCALLY,
    SYNC_PENDING,
    OUT_OF_SYNC,
    REVOKED
}

public enum VoucherStatus {
    DRAFT,
    ISSUED,
    SENT,
    RECEIVED_PENDING,
    OFFLINE_VERIFIED,
    SETTLEMENT_PENDING,
    SETTLED,
    REJECTED,
    CONFLICTED,
    EXPIRED,
    REFUNDED
}

public enum SettlementBatchStatus {
    CREATED,
    UPLOADED,
    VALIDATING,
    PARTIALLY_SETTLED,
    SETTLED,
    FAILED,
    CLOSED
}
```

### 4.2 모바일 클래스 설계

#### `SecureStateStore`

```java
public interface SecureStateStore {
    LocalCollateralState loadState(String collateralId);
    void saveState(LocalCollateralState state);
    void updateState(String collateralId, String newHash, long counter, java.math.BigDecimal remainingAmount);
    void revokeState(String collateralId);
}
```

구현 예:
- `AndroidKeystoreStateStore`
- `IOSKeychainStateStore`

#### `KeyManager`

```java
public interface KeyManager {
    DeviceKeyInfo generateKeyPair();
    byte[] sign(byte[] payload);
    java.security.PublicKey getPublicKey();
    int getKeyVersion();
}

public class DeviceKeyInfo {
    private String deviceId;
    private java.security.PublicKey publicKey;
    private int keyVersion;
}
```

#### `CanonicalSerializer`

```java
public interface CanonicalSerializer {
    byte[] serializeVoucher(VoucherProof proof);
}
```

구현 예:
- `DeterministicJsonSerializer`
- `CborSerializer`

#### `VoucherHashGenerator`

```java
public interface VoucherHashGenerator {
    String generateNewHash(
        String prevHash,
        java.math.BigDecimal amount,
        long counter,
        String nonce,
        String voucherId,
        int keyVersion,
        int policyVersion,
        long timestamp
    );
}
```

#### `NonceGenerator`

```java
public interface NonceGenerator {
    String generateNonce();
    String generateVoucherId();
}
```

#### `OfflinePaymentEngine`

```java
public class OfflinePaymentEngine {

    private final SecureStateStore secureStateStore;
    private final VoucherHashGenerator hashGenerator;
    private final CanonicalSerializer serializer;
    private final KeyManager keyManager;
    private final NonceGenerator nonceGenerator;

    public OfflinePaymentEngine(
        SecureStateStore secureStateStore,
        VoucherHashGenerator hashGenerator,
        CanonicalSerializer serializer,
        KeyManager keyManager,
        NonceGenerator nonceGenerator
    ) {
        this.secureStateStore = secureStateStore;
        this.hashGenerator = hashGenerator;
        this.serializer = serializer;
        this.keyManager = keyManager;
        this.nonceGenerator = nonceGenerator;
    }

    public VoucherProof createVoucher(String collateralId, java.math.BigDecimal amount) {
        // 1. state load
        // 2. amount validate
        // 3. nextCounter, nonce, voucherId 생성
        // 4. newHash 생성
        // 5. proof 생성
        // 6. canonical serialize
        // 7. sign
        // 8. local state update
        // 9. proof 반환
        return null;
    }
}
```

#### `ProofVerifier`

```java
public class ProofVerifier {

    private final CanonicalSerializer serializer;
    private final SignatureVerifier signatureVerifier;
    private final DuplicateVoucherChecker duplicateVoucherChecker;
    private final ExpirationPolicy expirationPolicy;

    public ProofVerifier(
        CanonicalSerializer serializer,
        SignatureVerifier signatureVerifier,
        DuplicateVoucherChecker duplicateVoucherChecker,
        ExpirationPolicy expirationPolicy
    ) {
        this.serializer = serializer;
        this.signatureVerifier = signatureVerifier;
        this.duplicateVoucherChecker = duplicateVoucherChecker;
        this.expirationPolicy = expirationPolicy;
    }

    public VerificationResult verifyOffline(VoucherProof proof, java.security.PublicKey issuerPublicKey) {
        // 1. format validation
        // 2. canonical serialization
        // 3. signature verification
        // 4. expiration check
        // 5. duplicate receiving check
        return null;
    }
}
```

#### 기타 모바일 구성요소

```java
public interface SignatureVerifier {
    boolean verify(byte[] payload, byte[] signature, java.security.PublicKey publicKey);
}

public interface DuplicateVoucherChecker {
    boolean isDuplicate(String voucherId, String nonce, String issuerDeviceId, long counter);
    void markReceived(VoucherProof proof);
}

public interface VoucherTransferManager {
    void send(VoucherProof proof);
    VoucherProof receive();
}
```

구현 예:
- `QrTransferManager`
- `BleTransferManager`
- `NfcTransferManager`

#### `SettlementSyncService`

```java
public class SettlementSyncService {
    public void uploadPendingProofs(java.util.List<VoucherProof> proofs, String idempotencyKey) {
        // pending proofs batch upload
    }
}
```

### 4.3 서버 클래스 설계

#### 도메인 모델

```java
public class Device {
    private String deviceId;
    private Long userId;
    private String publicKey;
    private int keyVersion;
    private DeviceStatus status;
}

public enum DeviceStatus {
    ACTIVE,
    REVOKED,
    FROZEN
}

public class CollateralPool {
    private String collateralId;
    private Long userId;
    private String deviceId;
    private java.math.BigDecimal lockedAmount;
    private java.math.BigDecimal remainingAmount;
    private String initialStateRoot;
    private int policyVersion;
    private CollateralStatus status;
}

public enum CollateralStatus {
    ACTIVE,
    FROZEN,
    SETTLED,
    CLOSED
}

public class SettlementResult {
    private String voucherId;
    private SettlementStatus status;
    private String reasonCode;
}

public enum SettlementStatus {
    SETTLED,
    REJECTED,
    CONFLICTED,
    EXPIRED,
    REFUNDED
}
```

#### 서비스 / 정책 구성요소

```java
public class DeviceRegistryService {

    public Device registerDevice(RegisterDeviceCommand command) {
        return null;
    }

    public void revokeDevice(String deviceId) {
    }

    public void rotateKey(String deviceId, int newKeyVersion, String publicKey) {
    }

    public Device getActiveDevice(String deviceId) {
        return null;
    }
}

public class CollateralService {

    public CollateralPool createCollateral(Long userId, String deviceId, java.math.BigDecimal amount) {
        return null;
    }

    public void freezeCollateralByDevice(String deviceId) {
    }

    public CollateralPool getCollateral(String collateralId) {
        return null;
    }
}

public class ProofSchemaValidator {
    public void validate(VoucherProof proof) {
        // 필수 필드, 길이, 형식, 버전 검증
    }
}

public class PublicKeyResolver {
    public java.security.PublicKey resolve(String issuerDeviceId, int keyVersion) {
        return null;
    }
}

public class SettlementPolicyEngine {
    public SettlementDecision decide(SettlementContext context) {
        return null;
    }
}

public class ProofChainValidator {
    public ChainValidationResult validateChain(CollateralPool collateral, java.util.List<VoucherProof> proofs) {
        return null;
    }
}

public class ConflictDetector {
    public ConflictResult detect(java.util.List<VoucherProof> incomingProofs, java.util.List<VoucherProof> existingProofs) {
        return null;
    }
}
```

#### `SettlementService`

```java
public class SettlementService {

    private final ProofSchemaValidator schemaValidator;
    private final ProofChainValidator chainValidator;
    private final ConflictDetector conflictDetector;
    private final SettlementPolicyEngine policyEngine;

    public SettlementService(
        ProofSchemaValidator schemaValidator,
        ProofChainValidator chainValidator,
        ConflictDetector conflictDetector,
        SettlementPolicyEngine policyEngine
    ) {
        this.schemaValidator = schemaValidator;
        this.chainValidator = chainValidator;
        this.conflictDetector = conflictDetector;
        this.policyEngine = policyEngine;
    }

    public java.util.List<SettlementResult> settleProofBatch(SettlementBatchCommand command) {
        // 1. validate schema
        // 2. load collateral/device/public key
        // 3. detect conflict
        // 4. validate chain
        // 5. apply policy
        // 6. persist results
        return null;
    }
}
```

#### 기타 서버 구성요소

```java
public class NonceReplayService {
    public boolean exists(String nonce, String issuerDeviceId) {
        return false;
    }

    public void save(String nonce, String issuerDeviceId, String voucherId) {
    }
}

public class RefundService {
    public void requestRefund(String voucherId) {
    }

    public void approveRefund(String voucherId) {
    }
}

public class ConflictLogService {
    public java.util.List<ConflictLog> getConflicts(ConflictSearchCondition condition) {
        return null;
    }

    public ConflictLog getConflictDetail(String conflictId) {
        return null;
    }
}
```

## 5. 패턴 적용 포인트

### 5.1 전략 패턴: 전송 채널 분리

```java
public interface TransferStrategy {
    void send(VoucherProof proof);
    VoucherProof receive();
}

public class QrTransferStrategy implements TransferStrategy {
    @Override
    public void send(VoucherProof proof) {
    }

    @Override
    public VoucherProof receive() {
        return null;
    }
}

public class BleTransferStrategy implements TransferStrategy {
    @Override
    public void send(VoucherProof proof) {
    }

    @Override
    public VoucherProof receive() {
        return null;
    }
}

public class NfcTransferStrategy implements TransferStrategy {
    @Override
    public void send(VoucherProof proof) {
    }

    @Override
    public VoucherProof receive() {
        return null;
    }
}
```

### 5.2 팩토리 패턴: 전송 방식 생성

```java
public class TransferStrategyFactory {

    public static TransferStrategy get(String type) {
        return switch (type) {
            case "QR" -> new QrTransferStrategy();
            case "BLE" -> new BleTransferStrategy();
            case "NFC" -> new NfcTransferStrategy();
            default -> throw new IllegalArgumentException("Unsupported transfer type");
        };
    }
}
```

### 5.3 템플릿 메서드: 정산 공통 흐름

```java
public abstract class AbstractSettlementProcessor {

    public final java.util.List<SettlementResult> process(SettlementBatchCommand command) {
        validateSchema(command);
        loadContext(command);
        detectConflict(command);
        validateChain(command);
        applyPolicy(command);
        return persist(command);
    }

    protected abstract void validateSchema(SettlementBatchCommand command);
    protected abstract void loadContext(SettlementBatchCommand command);
    protected abstract void detectConflict(SettlementBatchCommand command);
    protected abstract void validateChain(SettlementBatchCommand command);
    protected abstract void applyPolicy(SettlementBatchCommand command);
    protected abstract java.util.List<SettlementResult> persist(SettlementBatchCommand command);
}
```

## 6. 패키지 구조 예시

### 서버

```text
server/
 ├─ api/
 │   ├─ device/
 │   ├─ collateral/
 │   ├─ settlement/
 │   └─ admin/
 ├─ application/
 │   ├─ device/
 │   ├─ collateral/
 │   ├─ settlement/
 │   ├─ refund/
 │   └─ conflict/
 ├─ domain/
 │   ├─ device/
 │   ├─ collateral/
 │   ├─ proof/
 │   ├─ settlement/
 │   └─ policy/
 ├─ infrastructure/
 │   ├─ persistence/
 │   ├─ crypto/
 │   ├─ serializer/
 │   └─ logging/
 └─ common/
```

### 모바일

```text
mobile/
 ├─ app/
 │   ├─ registration/
 │   ├─ collateral/
 │   ├─ payment/
 │   ├─ receive/
 │   └─ settlement/
 ├─ domain/
 │   ├─ voucher/
 │   ├─ state/
 │   └─ policy/
 ├─ infrastructure/
 │   ├─ securestore/
 │   ├─ crypto/
 │   ├─ serializer/
 │   ├─ transport/
 │   └─ api/
 └─ common/
```

## 7. 구현 우선순위

### 1차 PoC
- `DeviceRegistryService`
- `CollateralService`
- `OfflinePaymentEngine`
- `ProofVerifier`
- `QrTransferStrategy`
- `SettlementService`
- `ConflictLogService` 최소 버전

### 2차
- BLE/NFC 전송
- 키 폐기/교체
- 환불 정책
- 관리자 상세 화면

### 3차
- attestation
- risk scoring
- 고도화된 충돌 판정

## 8. 핵심 설계 요약

### 모바일
- 송신자: 로컬 상태 전이 + 서명
- 수신자: 형식, 서명, 만료, 중복 수령 여부만 오프라인 검증
- 수신 자산: 정산 전 임시 상태

### 서버
- 서버가 최종 진실 원장
- 정산, 충돌, 만료, 폐기, 환불, 시간 위조 판정은 서버 정책 책임
- 오프라인 proof는 확정 자산이 아니라 정산 후보 데이터
