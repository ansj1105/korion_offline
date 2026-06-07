package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.CollateralStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProofChainValidatorTest {

    private final SpendingProofHashService hashService = new SpendingProofHashService();
    private final ProofChainValidator validator = new ProofChainValidator(
            new JsonService(new ObjectMapper()),
            hashService
    );

    @Test
    void validateAcceptsLegacyTwoDecimalStateHashForQueuedProofs() {
        CollateralLock collateral = new CollateralLock(
                "collateral-1",
                1L,
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                "KORI",
                new BigDecimal("100.00000000"),
                new BigDecimal("100.00000000"),
                "64155d36fc7f77f69ae784076df5055a259403e3e8d0a7fe9580c4ae2e5df93b",
                1,
                CollateralStatus.LOCKED,
                "lock-1",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflinePaymentProof previousProof = new OfflinePaymentProof(
                "proof-0",
                "batch-0",
                "voucher-0",
                "collateral-1",
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                "receiver-device",
                1,
                1,
                19,
                "nonce-previous",
                "64155d36fc7f77f69ae784076df5055a259403e3e8d0a7fe9580c4ae2e5df93b",
                "GENESIS",
                "signature",
                new BigDecimal("1.00000000"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{}",
                OffsetDateTime.now()
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-1",
                "batch-1",
                "voucher-1",
                "collateral-1",
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                "receiver-device",
                1,
                1,
                20,
                "nonce_050dada794e94b9b82974c33b2bdb41b",
                "b09218623be4bc048a4d36a8cb9e835fb0a5262a19a1b9a290eafb2f72dfe9d7",
                "64155d36fc7f77f69ae784076df5055a259403e3e8d0a7fe9580c4ae2e5df93b",
                "signature",
                new BigDecimal("1.00000000"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{}",
                OffsetDateTime.now()
        );

        ChainValidationResult result = validator.validate(collateral, List.of(previousProof), proof);

        assertTrue(result.valid());
    }

    @Test
    void validateAcceptsGenesisLinkForAggregateCollateralFirstProof() {
        CollateralLock aggregateCollateral = new CollateralLock(
                "collateral-aggregate",
                1L,
                "device-1",
                "KORI",
                new BigDecimal("10.00000000"),
                new BigDecimal("10.00000000"),
                "AGGREGATED",
                1,
                CollateralStatus.LOCKED,
                "lock-1,lock-2",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String newHash = hashService.computeNewStateHash("GENESIS", new BigDecimal("1"), 1, "device-1", "nonce-1");
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-1",
                "batch-1",
                "voucher-1",
                "collateral-1",
                "device-1",
                "receiver-device",
                1,
                1,
                1,
                "nonce-1",
                newHash,
                "GENESIS",
                "signature",
                new BigDecimal("1.00000000"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{}",
                OffsetDateTime.now()
        );

        ChainValidationResult result = validator.validate(aggregateCollateral, List.of(), proof);

        assertTrue(result.valid());
    }

    @Test
    void validateAcceptsLegacyDeviceScopedGenesisWhenCollateralStartsAtGenesis() {
        CollateralLock collateral = collateral("collateral-legacy", "device-1", "GENESIS");
        String previousHash = "GENESIS:device:device-1";
        String newHash = hashService.computeNewStateHash(previousHash, new BigDecimal("1"), 1, "device-1", "nonce-legacy");
        OfflinePaymentProof proof = proof("proof-legacy", "collateral-legacy", "device-1", 1, previousHash, newHash, "nonce-legacy");

        ChainValidationResult result = validator.validate(collateral, List.of(), proof);

        assertTrue(result.valid());
    }

    @Test
    void validateRejectsLegacyDeviceScopedGenesisForDifferentDevice() {
        CollateralLock collateral = collateral("collateral-legacy", "device-1", "GENESIS");
        String previousHash = "GENESIS:device:other-device";
        String newHash = hashService.computeNewStateHash(previousHash, new BigDecimal("1"), 1, "device-1", "nonce-legacy");
        OfflinePaymentProof proof = proof("proof-legacy", "collateral-legacy", "device-1", 1, previousHash, newHash, "nonce-legacy");

        ChainValidationResult result = validator.validate(collateral, List.of(), proof);

        assertFalse(result.valid());
        assertEquals(OfflinePayReasonCode.INVALID_GENESIS_LINK, result.reasonCode());
    }

    private CollateralLock collateral(String collateralId, String deviceId, String initialStateRoot) {
        return new CollateralLock(
                collateralId,
                1L,
                deviceId,
                "KORI",
                new BigDecimal("10.00000000"),
                new BigDecimal("10.00000000"),
                initialStateRoot,
                1,
                CollateralStatus.LOCKED,
                "lock-1",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private OfflinePaymentProof proof(
            String proofId,
            String collateralId,
            String senderDeviceId,
            long counter,
            String previousHash,
            String newHash,
            String nonce
    ) {
        return new OfflinePaymentProof(
                proofId,
                "batch-1",
                "voucher-1",
                collateralId,
                senderDeviceId,
                "receiver-device",
                1,
                1,
                counter,
                nonce,
                newHash,
                previousHash,
                "signature",
                new BigDecimal("1.00000000"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{}",
                OffsetDateTime.now()
        );
    }
}
