package io.korion.offlinepay.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.application.port.SettlementRepository;
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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IssuedProofApplicationServiceTest {

    private final DeviceRepository deviceRepository = Mockito.mock(DeviceRepository.class);
    private final CollateralRepository collateralRepository = Mockito.mock(CollateralRepository.class);
    private final IssuedOfflineProofRepository issuedOfflineProofRepository = Mockito.mock(IssuedOfflineProofRepository.class);
    private final SettlementRepository settlementRepository = Mockito.mock(SettlementRepository.class);
    private final ProofIssuerSignatureService proofIssuerSignatureService = Mockito.mock(ProofIssuerSignatureService.class);
    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final AppProperties properties = Mockito.mock(AppProperties.class);
    private final IssuedProofApplicationService service = new IssuedProofApplicationService(
            deviceRepository,
            collateralRepository,
            issuedOfflineProofRepository,
            settlementRepository,
            proofIssuerSignatureService,
            jsonService,
            properties
    );

    @Test
    void fallsBackToUserAssetCollateralWhenCurrentDeviceHasNoCollateral() {
        when(properties.assetCode()).thenReturn("KORI");
        when(properties.defaultCollateralExpiryHours()).thenReturn(24);
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndDeviceIdAndAssetCode(35L, "new-device", "KORI"))
                .thenReturn(List.of(collateral("large-lock", 35L, "new-device", "90")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(
                        collateral("small-lock", 35L, "old-device", "10"),
                        collateral("large-lock", 35L, "old-device", "90")
                ));
        when(settlementRepository.existsOpenByCollateralId("small-lock")).thenReturn(false);
        when(settlementRepository.existsOpenByCollateralId("large-lock")).thenReturn(false);
        when(collateralRepository.rebindDevice(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
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
                any(OffsetDateTime.class)
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
        assertThat(envelope.usableAmount()).isEqualTo("90");
        verify(collateralRepository, atLeastOnce()).rebindDevice(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void previousActiveProofDoesNotBlockCurrentSecurityDeviceProofIssue() {
        when(properties.assetCode()).thenReturn("KORI");
        when(properties.defaultCollateralExpiryHours()).thenReturn(24);
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndDeviceIdAndAssetCode(35L, "new-device", "KORI"))
                .thenReturn(List.of(collateral("active-proof-lock", 35L, "new-device", "90")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(collateral("active-proof-lock", 35L, "old-device", "90")));
        when(settlementRepository.existsOpenByCollateralId("active-proof-lock")).thenReturn(false);
        when(collateralRepository.rebindDevice(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
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
                any(OffsetDateTime.class)
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
    void issuesProofForAggregateCurrentDeviceCollateral() {
        when(properties.assetCode()).thenReturn("KORI");
        when(properties.defaultCollateralExpiryHours()).thenReturn(24);
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
        when(collateralRepository.findActiveByUserIdAndDeviceIdAndAssetCode(35L, "new-device", "KORI"))
                .thenReturn(List.of(
                        collateral("small-lock", 35L, "new-device", "10"),
                        collateral("large-lock", 35L, "new-device", "90")
                ));
        when(settlementRepository.existsOpenByCollateralId("small-lock")).thenReturn(false);
        when(settlementRepository.existsOpenByCollateralId("large-lock")).thenReturn(false);
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
                any(OffsetDateTime.class)
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
    void rejectsProofIssueWhenOnlyExpiredCollateralRemains() {
        when(properties.assetCode()).thenReturn("KORI");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(collateral("expired-lock", 35L, "new-device", "10", OffsetDateTime.now().minusDays(1))));
        when(collateralRepository.findActiveByUserIdAndDeviceIdAndAssetCode(35L, "new-device", "KORI"))
                .thenReturn(List.of(collateral("expired-lock", 35L, "new-device", "10", OffsetDateTime.now().minusDays(1))));
        when(settlementRepository.existsOpenByCollateralId("expired-lock")).thenReturn(false);

        assertThatThrownBy(() -> service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collateral expired for asset");

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
                any(OffsetDateTime.class)
        );
    }

    @Test
    void excludesExpiredCollateralFromAggregateProofBacking() {
        when(properties.assetCode()).thenReturn("KORI");
        when(properties.defaultCollateralExpiryHours()).thenReturn(24);
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
        when(collateralRepository.findActiveByUserIdAndDeviceIdAndAssetCode(35L, "new-device", "KORI"))
                .thenReturn(List.of(
                        collateral("expired-lock", 35L, "new-device", "10", OffsetDateTime.now().minusDays(1)),
                        collateral("valid-lock", 35L, "new-device", "25", OffsetDateTime.now().plusHours(1))
                ));
        when(settlementRepository.existsOpenByCollateralId("expired-lock")).thenReturn(false);
        when(settlementRepository.existsOpenByCollateralId("valid-lock")).thenReturn(false);
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
                any(OffsetDateTime.class)
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
        assertThat(envelope.collateralLockIds()).containsExactly("valid-lock");
        assertThat(envelope.usableAmount()).isEqualTo("25");
        assertThat(OffsetDateTime.parse(envelope.expiresAt())).isAfter(OffsetDateTime.now());
    }

    @Test
    void renewsExpiredCollateralForCurrentSecurityDeviceBeforeProofIssue() {
        when(properties.assetCode()).thenReturn("KORI");
        when(properties.defaultCollateralExpiryHours()).thenReturn(24);
        when(proofIssuerSignatureService.keyId()).thenReturn("issuer-1");
        when(proofIssuerSignatureService.publicKey()).thenReturn("issuer-public-key");
        when(proofIssuerSignatureService.sign(anyString())).thenReturn("issuer-signature");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));

        CollateralLock expired = collateral("expired-lock", 35L, "new-device", "10", OffsetDateTime.now().minusHours(1));
        CollateralLock renewed = collateral("expired-lock", 35L, "new-device", "10", OffsetDateTime.now().plusHours(24));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(expired));
        when(collateralRepository.findActiveByUserIdAndDeviceIdAndAssetCode(35L, "new-device", "KORI"))
                .thenReturn(List.of(expired))
                .thenReturn(List.of(renewed));
        when(settlementRepository.existsOpenByCollateralId("expired-lock")).thenReturn(false);
        when(collateralRepository.renewExpiry(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class), anyString()))
                .thenReturn(true);
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
                any(OffsetDateTime.class)
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
        assertThat(OffsetDateTime.parse(envelope.expiresAt())).isAfter(OffsetDateTime.now());
        verify(collateralRepository).renewExpiry(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class), anyString());
    }

    @Test
    void blocksDeviceRebindWhenCollateralHasOpenSettlement() {
        when(properties.assetCode()).thenReturn("KORI");
        when(deviceRepository.findByUserIdAndDeviceId(35L, "new-device"))
                .thenReturn(Optional.of(device(35L, "new-device")));
        when(collateralRepository.findActiveByUserIdAndAssetCode(35L, "KORI"))
                .thenReturn(List.of(collateral("settlement-lock", 35L, "old-device", "90")));
        when(collateralRepository.findActiveByUserIdAndDeviceIdAndAssetCode(35L, "new-device", "KORI"))
                .thenReturn(List.of());
        when(settlementRepository.existsOpenByCollateralId("settlement-lock")).thenReturn(true);

        assertThatThrownBy(() -> service.issue(
                new IssuedProofApplicationService.IssueCommand(35L, "new-device", "KORI")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collateral device sync blocked");
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
}
