4. SettlementService 실제 코드 구현 (Node / Java 둘 다)

기준은 네 시퀀스 문서의 SettlementService 흐름이다.

4-1. Node.js + TypeScript 버전

아래는 실제 서비스 골격이다.
Fastify + repository 주입 구조를 가정했다.

export type SettlementStatus =
  | 'SETTLED'
  | 'REJECTED'
  | 'CONFLICTED'
  | 'EXPIRED'
  | 'REFUNDED';

export interface VoucherProof {
  voucherId: string;
  collateralId: string;
  issuerDeviceId: string;
  receiverDeviceId?: string;
  keyVersion: number;
  policyVersion: number;
  prevHash: string;
  newHash: string;
  amount: string;
  counter: number;
  nonce: string;
  timestamp: number;
  expiresAt: number;
  signature: string;
  canonicalPayload?: string;
}

export interface SettlementBatchCommand {
  batchId: string;
  uploaderType: 'SENDER' | 'RECEIVER';
  uploaderDeviceId: string;
  proofs: VoucherProof[];
}

export interface SettlementResult {
  voucherId: string;
  status: SettlementStatus;
  reasonCode?: string;
  detail?: Record<string, unknown>;
}

export interface ProofSchemaValidator {
  validate(proof: VoucherProof): void;
}

export interface PublicKeyResolver {
  resolve(deviceId: string, keyVersion: number): Promise<string>;
}

export interface SignatureVerifier {
  verify(payload: string, signature: string, publicKey: string): boolean;
}

export interface CanonicalSerializer {
  serialize(proof: VoucherProof): string;
}

export interface CollateralRepository {
  findByCollateralId(collateralId: string): Promise<CollateralLock | null>;
  deductRemainingAmount(collateralId: string, amount: string): Promise<void>;
}

export interface ProofRepository {
  findExistingByVoucherId(voucherId: string): Promise<VoucherProofRecord | null>;
  findBySenderNonce(senderDeviceId: string, nonce: string): Promise<VoucherProofRecord | null>;
  findByCollateralId(collateralId: string): Promise<VoucherProofRecord[]>;
  saveBatch(batchId: string, uploaderType: 'SENDER' | 'RECEIVER', proofs: VoucherProof[]): Promise<void>;
}

export interface SettlementRepository {
  existsByVoucherId(voucherId: string): Promise<boolean>;
  saveResults(batchId: string, results: SettlementResult[]): Promise<void>;
}

export interface ConflictLogRepository {
  save(conflict: ConflictLog): Promise<void>;
}

export interface DeviceRepository {
  findActive(deviceId: string, keyVersion: number): Promise<Device | null>;
}

export interface SettlementPolicyEngine {
  evaluate(ctx: SettlementContext): SettlementDecision;
}

export interface ProofChainValidator {
  validate(collateral: CollateralLock, allProofs: VoucherProofRecord[], incomingProof: VoucherProof): ChainValidationResult;
}

export interface ConflictDetector {
  detect(existingProofs: VoucherProofRecord[], incomingProof: VoucherProof): ConflictDetectionResult;
}

export interface TxManager {
  runInTransaction<T>(callback: () => Promise<T>): Promise<T>;
}

export interface CollateralLock {
  collateralId: string;
  deviceId: string;
  remainingAmount: string;
  initialStateRoot: string;
  policyVersion: number;
  status: 'ACTIVE' | 'FROZEN' | 'SETTLED' | 'CLOSED';
  expiresAt: Date;
}

export interface VoucherProofRecord {
  voucherId: string;
  collateralId: string;
  senderDeviceId: string;
  nonce: string;
  counter: number;
  prevHash: string;
  newHash: string;
  amount: string;
}

export interface Device {
  deviceId: string;
  keyVersion: number;
  status: 'ACTIVE' | 'REVOKED' | 'FROZEN';
}

export interface ConflictDetectionResult {
  conflicted: boolean;
  type?: string;
  detail?: Record<string, unknown>;
}

export interface ChainValidationResult {
  valid: boolean;
  reasonCode?: string;
  detail?: Record<string, unknown>;
}

export interface SettlementDecision {
  status: SettlementStatus;
  reasonCode?: string;
  detail?: Record<string, unknown>;
}

export interface SettlementContext {
  proof: VoucherProof;
  collateral: CollateralLock;
  device: Device;
}

export interface ConflictLog {
  voucherId: string;
  collateralId: string;
  deviceId: string;
  conflictType: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  detail: Record<string, unknown>;
}

export class SettlementService {
  constructor(
    private readonly schemaValidator: ProofSchemaValidator,
    private readonly publicKeyResolver: PublicKeyResolver,
    private readonly signatureVerifier: SignatureVerifier,
    private readonly serializer: CanonicalSerializer,
    private readonly collateralRepository: CollateralRepository,
    private readonly proofRepository: ProofRepository,
    private readonly settlementRepository: SettlementRepository,
    private readonly conflictLogRepository: ConflictLogRepository,
    private readonly deviceRepository: DeviceRepository,
    private readonly policyEngine: SettlementPolicyEngine,
    private readonly chainValidator: ProofChainValidator,
    private readonly conflictDetector: ConflictDetector,
    private readonly txManager: TxManager,
  ) {}

  async settleProofBatch(command: SettlementBatchCommand): Promise<SettlementResult[]> {
    return this.txManager.runInTransaction(async () => {
      await this.proofRepository.saveBatch(command.batchId, command.uploaderType, command.proofs);

      const results: SettlementResult[] = [];

      for (const proof of command.proofs) {
        const result = await this.settleSingleProof(proof);
        results.push(result);
      }

      await this.settlementRepository.saveResults(command.batchId, results);
      return results;
    });
  }

  private async settleSingleProof(proof: VoucherProof): Promise<SettlementResult> {
    try {
      this.schemaValidator.validate(proof);

      const collateral = await this.collateralRepository.findByCollateralId(proof.collateralId);
      if (!collateral) {
        return this.reject(proof.voucherId, 'COLLATERAL_NOT_FOUND');
      }

      const device = await this.deviceRepository.findActive(proof.issuerDeviceId, proof.keyVersion);
      if (!device) {
        return this.reject(proof.voucherId, 'DEVICE_OR_KEY_NOT_ACTIVE');
      }

      const publicKey = await this.publicKeyResolver.resolve(proof.issuerDeviceId, proof.keyVersion);
      const payload = this.serializer.serialize(proof);

      const verified = this.signatureVerifier.verify(payload, proof.signature, publicKey);
      if (!verified) {
        return this.reject(proof.voucherId, 'INVALID_SIGNATURE');
      }

      const alreadySettled = await this.settlementRepository.existsByVoucherId(proof.voucherId);
      if (alreadySettled) {
        return this.conflict(proof.voucherId, 'DUPLICATE_SETTLEMENT');
      }

      const existingProofs = await this.proofRepository.findByCollateralId(proof.collateralId);

      const conflictResult = this.conflictDetector.detect(existingProofs, proof);
      if (conflictResult.conflicted) {
        await this.conflictLogRepository.save({
          voucherId: proof.voucherId,
          collateralId: proof.collateralId,
          deviceId: proof.issuerDeviceId,
          conflictType: conflictResult.type ?? 'UNKNOWN_CONFLICT',
          severity: 'HIGH',
          detail: conflictResult.detail ?? {},
        });

        return this.conflict(proof.voucherId, conflictResult.type ?? 'UNKNOWN_CONFLICT', conflictResult.detail);
      }

      const chainResult = this.chainValidator.validate(collateral, existingProofs, proof);
      if (!chainResult.valid) {
        return this.reject(proof.voucherId, chainResult.reasonCode ?? 'INVALID_CHAIN', chainResult.detail);
      }

      const decision = this.policyEngine.evaluate({
        proof,
        collateral,
        device,
      });

      if (decision.status === 'SETTLED') {
        await this.collateralRepository.deductRemainingAmount(proof.collateralId, proof.amount);
      }

      return {
        voucherId: proof.voucherId,
        status: decision.status,
        reasonCode: decision.reasonCode,
        detail: decision.detail,
      };
    } catch (error) {
      return this.reject(proof.voucherId, 'UNEXPECTED_ERROR', {
        message: error instanceof Error ? error.message : 'unknown',
      });
    }
  }

  private reject(voucherId: string, reasonCode: string, detail?: Record<string, unknown>): SettlementResult {
    return {
      voucherId,
      status: 'REJECTED',
      reasonCode,
      detail,
    };
  }

  private conflict(voucherId: string, reasonCode: string, detail?: Record<string, unknown>): SettlementResult {
    return {
      voucherId,
      status: 'CONFLICTED',
      reasonCode,
      detail,
    };
  }
}
4-2. Node 구현에서 반드시 추가할 것
금액 차감은 원자적이어야 함

단순 update 금지. 아래처럼 가야 함.

UPDATE collateral_locks
SET remaining_amount = remaining_amount - $1,
    updated_at = NOW()
WHERE collateral_id = $2
  AND status = 'ACTIVE'
  AND remaining_amount >= $1;

그리고 rowCount = 0이면 초과 사용으로 처리.

4-3. Java 버전

Spring Boot 서비스 기준으로 작성했다.

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SettlementService {

    private final ProofSchemaValidator schemaValidator;
    private final PublicKeyResolver publicKeyResolver;
    private final SignatureVerifier signatureVerifier;
    private final CanonicalSerializer serializer;
    private final CollateralRepository collateralRepository;
    private final ProofRepository proofRepository;
    private final SettlementRepository settlementRepository;
    private final ConflictLogRepository conflictLogRepository;
    private final DeviceRepository deviceRepository;
    private final SettlementPolicyEngine policyEngine;
    private final ProofChainValidator chainValidator;
    private final ConflictDetector conflictDetector;

    public SettlementService(
        ProofSchemaValidator schemaValidator,
        PublicKeyResolver publicKeyResolver,
        SignatureVerifier signatureVerifier,
        CanonicalSerializer serializer,
        CollateralRepository collateralRepository,
        ProofRepository proofRepository,
        SettlementRepository settlementRepository,
        ConflictLogRepository conflictLogRepository,
        DeviceRepository deviceRepository,
        SettlementPolicyEngine policyEngine,
        ProofChainValidator chainValidator,
        ConflictDetector conflictDetector
    ) {
        this.schemaValidator = schemaValidator;
        this.publicKeyResolver = publicKeyResolver;
        this.signatureVerifier = signatureVerifier;
        this.serializer = serializer;
        this.collateralRepository = collateralRepository;
        this.proofRepository = proofRepository;
        this.settlementRepository = settlementRepository;
        this.conflictLogRepository = conflictLogRepository;
        this.deviceRepository = deviceRepository;
        this.policyEngine = policyEngine;
        this.chainValidator = chainValidator;
        this.conflictDetector = conflictDetector;
    }

    public List<SettlementResult> settleProofBatch(SettlementBatchCommand command) {
        proofRepository.saveBatch(command.getBatchId(), command.getUploaderType(), command.getProofs());

        List<SettlementResult> results = new ArrayList<>();

        for (VoucherProof proof : command.getProofs()) {
            results.add(settleSingleProof(proof));
        }

        settlementRepository.saveResults(command.getBatchId(), results);
        return results;
    }

    private SettlementResult settleSingleProof(VoucherProof proof) {
        try {
            schemaValidator.validate(proof);

            CollateralPool collateral = collateralRepository.findByCollateralId(proof.getCollateralId());
            if (collateral == null) {
                return reject(proof.getVoucherId(), "COLLATERAL_NOT_FOUND", null);
            }

            Device device = deviceRepository.findActive(proof.getIssuerDeviceId(), proof.getKeyVersion());
            if (device == null) {
                return reject(proof.getVoucherId(), "DEVICE_OR_KEY_NOT_ACTIVE", null);
            }

            java.security.PublicKey publicKey =
                publicKeyResolver.resolve(proof.getIssuerDeviceId(), proof.getKeyVersion());

            byte[] payload = serializer.serializeVoucher(proof);

            boolean verified = signatureVerifier.verify(payload, proof.getSignature(), publicKey);
            if (!verified) {
                return reject(proof.getVoucherId(), "INVALID_SIGNATURE", null);
            }

            boolean alreadySettled = settlementRepository.existsByVoucherId(proof.getVoucherId());
            if (alreadySettled) {
                return conflict(proof.getVoucherId(), "DUPLICATE_SETTLEMENT", null);
            }

            List<VoucherProofRecord> existingProofs =
                proofRepository.findByCollateralId(proof.getCollateralId());

            ConflictResult conflictResult = conflictDetector.detect(existingProofs, proof);
            if (conflictResult.isConflicted()) {
                conflictLogRepository.save(new ConflictLog(
                    proof.getVoucherId(),
                    proof.getCollateralId(),
                    proof.getIssuerDeviceId(),
                    conflictResult.getType(),
                    "HIGH",
                    conflictResult.getDetail()
                ));

                return conflict(
                    proof.getVoucherId(),
                    conflictResult.getType(),
                    conflictResult.getDetail()
                );
            }

            ChainValidationResult chainResult =
                chainValidator.validateChain(collateral, existingProofs, proof);

            if (!chainResult.isValid()) {
                return reject(
                    proof.getVoucherId(),
                    chainResult.getReasonCode(),
                    chainResult.getDetail()
                );
            }

            SettlementDecision decision = policyEngine.decide(new SettlementContext(
                proof,
                collateral,
                device
            ));

            if (decision.getStatus() == SettlementStatus.SETTLED) {
                collateralRepository.deductRemainingAmount(
                    proof.getCollateralId(),
                    proof.getAmount()
                );
            }

            SettlementResult result = new SettlementResult();
            result.setVoucherId(proof.getVoucherId());
            result.setStatus(decision.getStatus());
            result.setReasonCode(decision.getReasonCode());
            return result;

        } catch (Exception e) {
            return reject(
                proof.getVoucherId(),
                "UNEXPECTED_ERROR",
                Map.of("message", e.getMessage())
            );
        }
    }

    private SettlementResult reject(String voucherId, String reasonCode, Map<String, Object> detail) {
        SettlementResult result = new SettlementResult();
        result.setVoucherId(voucherId);
        result.setStatus(SettlementStatus.REJECTED);
        result.setReasonCode(reasonCode);
        result.setDetail(detail);
        return result;
    }

    private SettlementResult conflict(String voucherId, String reasonCode, Map<String, Object> detail) {
        SettlementResult result = new SettlementResult();
        result.setVoucherId(voucherId);
        result.setStatus(SettlementStatus.CONFLICTED);
        result.setReasonCode(reasonCode);
        result.setDetail(detail);
        return result;
    }
}