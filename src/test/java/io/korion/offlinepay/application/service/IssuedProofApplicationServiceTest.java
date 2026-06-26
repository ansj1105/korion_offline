package io.korion.offlinepay.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IssuedProofApplicationServiceTest {

    private final DeviceRepository deviceRepository = Mockito.mock(DeviceRepository.class);
    private final CollateralRepository collateralRepository = Mockito.mock(CollateralRepository.class);
    private final IssuedOfflineProofRepository issuedOfflineProofRepository = Mockito.mock(IssuedOfflineProofRepository.class);
    private final ProofIssuerSignatureService proofIssuerSignatureService = Mockito.mock(ProofIssuerSignatureService.class);
    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final JsonPayloadCanonicalizationService jsonPayloadCanonicalizationService =
            new JsonPayloadCanonicalizationService(jsonService);
    private final AppProperties properties = Mockito.mock(AppProperties.class);
    private final IssuedProofApplicationService service = new IssuedProofApplicationService(
            deviceRepository,
            collateralRepository,
            issuedOfflineProofRepository,
            proofIssuerSignatureService,
            jsonService,
            jsonPayloadCanonicalizationService,
            properties
    );

    @BeforeEach
    void setUp() {
        when(proofIssuerSignatureService.verify(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void issuesProofFromUserAssetCollateralWithoutRebindingLocksToCurrentDevice() {
        when(properties.assetCode()).thenReturn("KORI");
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(
                        collateral("small-lock", 35L, "old-device", "10"),
                        collateral("large-lock", 35L, "old-device", "90")
                ));
        when(issuedOfflineProofRepository.save(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                any(BigDecimal.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(IssuedProofStatus.class),
                nullable(OffsetDateTime.class)
        )).thenAnswer(invocation -> new IssuedOfflineProof(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5),
                invocation.getArgument(6),
                invocation.getArgument(7),
                invocation.getArgument(8),
                invocation.getArgument(9),
                invocation.getArgument(10),
                invocation.getArgument(11),
                null,
                invocation.getArgument(12),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        IssuedProofApplicationService.IssuedProofEnvelope envelope = service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        );

        assertThat(envelope.deviceId()).isEqualTo("new-device");
        assertThat(envelope.collateralLockId()).isEqualTo("large-lock");
        assertThat(envelope.collateralLockIds()).containsExactly("small-lock", "large-lock");
        assertThat(envelope.usableAmount()).isEqualTo("100");
    }

    @Test
    void previousActiveProofDoesNotBlockCurrentSecurityDeviceProofIssue() {
        when(properties.assetCode()).thenReturn("KORI");
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(collateral("active-proof-lock", 35L, "old-device", "90")));
        when(issuedOfflineProofRepository.save(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                any(BigDecimal.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(IssuedProofStatus.class),
                nullable(OffsetDateTime.class)
        )).thenAnswer(invocation -> new IssuedOfflineProof(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5),
                invocation.getArgument(6),
                invocation.getArgument(7),
                invocation.getArgument(8),
                invocation.getArgument(9),
                invocation.getArgument(10),
                invocation.getArgument(11),
                null,
                invocation.getArgument(12),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        IssuedProofApplicationService.IssuedProofEnvelope envelope = service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        );

        assertThat(envelope.collateralLockId()).isEqualTo("active-proof-lock");
        verify(issuedOfflineProofRepository, never()).updateStatus(anyString(), any(IssuedProofStatus.class), any());
    }

    @Test
    void reusesExistingProofOnlyWhenItMatchesCurrentAggregateBacking() {
        when(properties.assetCode()).thenReturn("KORI");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(
                        collateral("small-lock", 35L, "old-device", "10"),
                        collateral("large-lock", 35L, "old-device", "90")
                ));
        when(issuedOfflineProofRepository.findLatestActiveByUserIdAndDeviceIdAndAssetCode(35L, "new-device", "KORI"))
                .thenReturn(Optional.of(issuedProof(
                        "existing-proof",
                        "large-lock",
                        "100",
                        "[\"small-lock\",\"large-lock\"]"
                )));

        IssuedProofApplicationService.IssuedProofEnvelope envelope = service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        );

        assertThat(envelope.proofId()).isEqualTo("existing-proof");
        assertThat(envelope.usableAmount()).isEqualTo("100");
        verify(issuedOfflineProofRepository, never())
                .revokeActiveByUserIdAndDeviceIdAndAssetCode(anyLong(), anyString(), anyString());
        verify(issuedOfflineProofRepository, never()).save(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                any(BigDecimal.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(IssuedProofStatus.class),
                nullable(OffsetDateTime.class)
        );
    }

    @Test
    void revokesStaleActiveProofAndIssuesFreshWhenCurrentAggregateChanged() {
        when(properties.assetCode()).thenReturn("KORI");
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(
                        collateral("small-lock", 35L, "old-device", "10"),
                        collateral("large-lock", 35L, "old-device", "50")
                ));
        when(issuedOfflineProofRepository.findLatestActiveByUserIdAndDeviceIdAndAssetCode(35L, "new-device", "KORI"))
                .thenReturn(Optional.of(issuedProof(
                        "stale-proof",
                        "large-lock",
                        "64",
                        "[\"small-lock\",\"large-lock\"]"
                )));
        when(issuedOfflineProofRepository.save(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                any(BigDecimal.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(IssuedProofStatus.class),
                nullable(OffsetDateTime.class)
        )).thenAnswer(invocation -> new IssuedOfflineProof(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5),
                invocation.getArgument(6),
                invocation.getArgument(7),
                invocation.getArgument(8),
                invocation.getArgument(9),
                invocation.getArgument(10),
                invocation.getArgument(11),
                null,
                invocation.getArgument(12),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        IssuedProofApplicationService.IssuedProofEnvelope envelope = service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        );

        assertThat(envelope.collateralLockId()).isEqualTo("large-lock");
        assertThat(envelope.usableAmount()).isEqualTo("60");
        verify(issuedOfflineProofRepository)
                .revokeActiveByUserIdAndDeviceIdAndAssetCode(35L, "new-device", "KORI");
    }

    @Test
    void issuesProofForAggregateCurrentDeviceCollateral() {
        when(properties.assetCode()).thenReturn("KORI");
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(
                        collateral("small-lock", 35L, "new-device", "10"),
                        collateral("large-lock", 35L, "new-device", "90")
                ));
        when(issuedOfflineProofRepository.save(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                any(BigDecimal.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(IssuedProofStatus.class),
                nullable(OffsetDateTime.class)
        )).thenAnswer(invocation -> new IssuedOfflineProof(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5),
                invocation.getArgument(6),
                invocation.getArgument(7),
                invocation.getArgument(8),
                invocation.getArgument(9),
                invocation.getArgument(10),
                invocation.getArgument(11),
                null,
                invocation.getArgument(12),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        IssuedProofApplicationService.IssuedProofEnvelope envelope = service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        );

        assertThat(envelope.collateralLockId()).isEqualTo("large-lock");
        assertThat(envelope.collateralLockIds()).containsExactly("small-lock", "large-lock");
        assertThat(envelope.usableAmount()).isEqualTo("100");
        assertThat(envelope.status()).isEqualTo("ACTIVE");
        assertThat(envelope.issuedPayload()).contains("\"collateralLockIds\":[\"small-lock\",\"large-lock\"]");
    }

    @Test
    void issuesProofWhenOnlyExpiredCollateralRemains() {
        when(properties.assetCode()).thenReturn("KORI");
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(collateral("expired-lock", 35L, "new-device", "10", OffsetDateTime.now().minusDays(1))));
        when(issuedOfflineProofRepository.save(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                any(BigDecimal.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(IssuedProofStatus.class),
                nullable(OffsetDateTime.class)
        )).thenAnswer(invocation -> new IssuedOfflineProof(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5),
                invocation.getArgument(6),
                invocation.getArgument(7),
                invocation.getArgument(8),
                invocation.getArgument(9),
                invocation.getArgument(10),
                invocation.getArgument(11),
                null,
                invocation.getArgument(12),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        IssuedProofApplicationService.IssuedProofEnvelope envelope = service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        );

        assertThat(envelope.collateralLockId()).isEqualTo("expired-lock");
        assertThat(envelope.usableAmount()).isEqualTo("10");
        assertThat(envelope.expiresAt()).isEmpty();
    }

    @Test
    void includesExpiredCollateralInAggregateProofBacking() {
        when(properties.assetCode()).thenReturn("KORI");
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(
                        collateral("expired-lock", 35L, "new-device", "10", OffsetDateTime.now().minusDays(1)),
                        collateral("valid-lock", 35L, "new-device", "25", OffsetDateTime.now().plusHours(1))
                ));
        when(issuedOfflineProofRepository.save(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                any(BigDecimal.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(IssuedProofStatus.class),
                nullable(OffsetDateTime.class)
        )).thenAnswer(invocation -> new IssuedOfflineProof(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5),
                invocation.getArgument(6),
                invocation.getArgument(7),
                invocation.getArgument(8),
                invocation.getArgument(9),
                invocation.getArgument(10),
                invocation.getArgument(11),
                null,
                invocation.getArgument(12),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        IssuedProofApplicationService.IssuedProofEnvelope envelope = service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        );

        assertThat(envelope.collateralLockId()).isEqualTo("valid-lock");
        assertThat(envelope.collateralLockIds()).containsExactly("expired-lock", "valid-lock");
        assertThat(envelope.usableAmount()).isEqualTo("35");
        assertThat(envelope.expiresAt()).isEmpty();
    }

    @Test
    void doesNotRenewExpiredCollateralBeforeProofIssue() {
        when(properties.assetCode()).thenReturn("KORI");
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));

        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(collateral("expired-lock", 35L, "new-device", "10", OffsetDateTime.now().minusHours(1))));
        when(issuedOfflineProofRepository.save(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                any(BigDecimal.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(IssuedProofStatus.class),
                nullable(OffsetDateTime.class)
        )).thenAnswer(invocation -> new IssuedOfflineProof(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5),
                invocation.getArgument(6),
                invocation.getArgument(7),
                invocation.getArgument(8),
                invocation.getArgument(9),
                invocation.getArgument(10),
                invocation.getArgument(11),
                null,
                invocation.getArgument(12),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        IssuedProofApplicationService.IssuedProofEnvelope envelope = service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        );

        assertThat(envelope.collateralLockId()).isEqualTo("expired-lock");
        assertThat(envelope.usableAmount()).isEqualTo("10");
        assertThat(envelope.expiresAt()).isEmpty();
    }

    @Test
    void openSettlementDoesNotBlockExpiredCollateralProofIssue() {
        when(properties.assetCode()).thenReturn("KORI");
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(collateral("settlement-lock", 35L, "old-device", "90", OffsetDateTime.now().minusHours(1))));
        when(issuedOfflineProofRepository.save(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                any(BigDecimal.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(IssuedProofStatus.class),
                nullable(OffsetDateTime.class)
        )).thenAnswer(invocation -> new IssuedOfflineProof(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5),
                invocation.getArgument(6),
                invocation.getArgument(7),
                invocation.getArgument(8),
                invocation.getArgument(9),
                invocation.getArgument(10),
                invocation.getArgument(11),
                null,
                invocation.getArgument(12),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        IssuedProofApplicationService.IssuedProofEnvelope envelope = service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        );

        assertThat(envelope.collateralLockId()).isEqualTo("settlement-lock");
        assertThat(envelope.usableAmount()).isEqualTo("90");
        assertThat(envelope.expiresAt()).isEmpty();
    }

    private static Device device(long userId, String deviceId) {
        return new Device(
                "device-row",
                deviceId,
                userId,
                "device-public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private static CollateralLock collateral(String id, long userId, String deviceId, String remainingAmount) {
        return collateral(id, userId, deviceId, remainingAmount, OffsetDateTime.now().plusHours(1));
    }

    private static CollateralLock collateral(String id, long userId, String deviceId, String remainingAmount, OffsetDateTime expiresAt) {
        BigDecimal amount = new BigDecimal(remainingAmount);
        return new CollateralLock(
                id,
                userId,
                deviceId,
                "KORI",
                amount,
                amount,
                "state-root",
                1,
                CollateralStatus.LOCKED,
                "external-lock",
                expiresAt,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private static IssuedOfflineProof issuedProof(
            String proofId,
            String collateralId,
            String usableAmount,
            String collateralLockIdsJson
    ) {
        String payload = """
                {"nonce":"proof_nonce","userId":35,"proofId":"%s","deviceId":"new-device","assetCode":"KORI","usableAmount":"%s","collateralLockId":"%s","collateralLockIds":%s}
                """.formatted(proofId, usableAmount, collateralId, collateralLockIdsJson);
        return new IssuedOfflineProof(
                proofId,
                35L,
                "new-device",
                collateralId,
                "KORI",
                new BigDecimal(usableAmount),
                "proof_nonce",
                "issuer-1",
                "issuer-public-key",
                "issuer-signature",
                payload,
                IssuedProofStatus.ACTIVE,
                null,
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
