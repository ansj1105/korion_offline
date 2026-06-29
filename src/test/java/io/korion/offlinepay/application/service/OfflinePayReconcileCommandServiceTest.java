package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflinePayReconcileCommandRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePayReconcileCommand;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflinePayReconcileCommandStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OfflinePayReconcileCommandServiceTest {

    private final DeviceRepository deviceRepository = Mockito.mock(DeviceRepository.class);
    private final OfflineLedgerService offlineLedgerService = Mockito.mock(OfflineLedgerService.class);
    private final OfflinePayReconcileCommandRepository commandRepository = Mockito.mock(OfflinePayReconcileCommandRepository.class);
    private final ProofIssuerSignatureService signatureService = new ProofIssuerSignatureService(
            new AppProperties("KORI", 0, 0, 0, null, null, null, null, null, null)
    );
    private final OffsetDateTime now = OffsetDateTime.ofInstant(Instant.parse("2026-06-22T01:02:03Z"), ZoneOffset.UTC);
    private OfflinePayReconcileCommandService service;

    @BeforeEach
    void setUp() {
        service = new OfflinePayReconcileCommandService(
                deviceRepository,
                offlineLedgerService,
                commandRepository,
                signatureService,
                Clock.fixed(now.toInstant(), ZoneOffset.UTC)
        );
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device(DeviceStatus.ACTIVE)));
        when(offlineLedgerService.getHubSummary("device-1", "KORI")).thenReturn(summary("2.000000", "10.000000", 1));
    }

    @Test
    void createsCommandWhenLocalSummaryHasProjectionGap() {
        OfflinePayReconcileCommand created = command("command-1", OfflinePayReconcileCommandStatus.PENDING);
        OfflinePayReconcileCommand delivered = command("command-1", OfflinePayReconcileCommandStatus.DELIVERED);
        when(commandRepository.findRunnableByUserIdAndAssetCode(eq(1L), eq("KORI"), any()))
                .thenReturn(Optional.empty());
        when(commandRepository.create(eq(1L), eq("KORI"), eq("LOCAL_COLLATERAL_SERVER_PROJECTION_GAP"), any(), any(), any(), any()))
                .thenReturn(created);
        when(commandRepository.markDelivered("command-1", "device-1")).thenReturn(delivered);

        OfflinePayReconcileCommandService.PollResponse response = service.poll(
                new OfflinePayReconcileCommandService.PollCommand(
                        "device-1",
                        "KORI",
                        Map.of("offlineAvailableAmount", "9.000000", "pendingCount", 1, "unsettledReceivedAmount", "2.000000")
                )
        );

        assertTrue(response.hasCommand());
        assertEquals("RECONCILE_COMMAND_AVAILABLE", response.reasonCode());
        assertEquals("command-1", response.command().id());
        assertTrue(response.command().signingPayload().startsWith("RECONCILE_COMMAND_V1|command-1|1|KORI"));
        assertTrue(signatureService.verify(
                response.command().signingPayload(),
                response.command().signingPublicKey(),
                response.command().signature()
        ));
    }

    @Test
    void createsCommandWhenLocalPendingExceedsServerPendingProjection() {
        OfflinePayReconcileCommand created = command(
                "command-pending-gap",
                OfflinePayReconcileCommandStatus.PENDING,
                "LOCAL_PENDING_SERVER_PROJECTION_GAP"
        );
        OfflinePayReconcileCommand delivered = command(
                "command-pending-gap",
                OfflinePayReconcileCommandStatus.DELIVERED,
                "LOCAL_PENDING_SERVER_PROJECTION_GAP"
        );
        when(offlineLedgerService.getHubSummary("device-1", "KORI"))
                .thenReturn(summary("2.000000", "10.000000", 0));
        when(commandRepository.findRunnableByUserIdAndAssetCode(eq(1L), eq("KORI"), any()))
                .thenReturn(Optional.empty());
        when(commandRepository.create(eq(1L), eq("KORI"), eq("LOCAL_PENDING_SERVER_PROJECTION_GAP"), any(), any(), any(), any()))
                .thenReturn(created);
        when(commandRepository.markDelivered("command-pending-gap", "device-1")).thenReturn(delivered);

        OfflinePayReconcileCommandService.PollResponse response = service.poll(
                new OfflinePayReconcileCommandService.PollCommand(
                        "device-1",
                        "KORI",
                        Map.of("offlineAvailableAmount", "10.000000", "pendingCount", 1, "unsettledReceivedAmount", "2.000000")
                )
        );

        assertTrue(response.hasCommand());
        assertEquals("LOCAL_PENDING_SERVER_PROJECTION_GAP", response.command().reasonCode());
    }

    @Test
    void returnsNoopWhenNoGapAndNoExistingCommand() {
        when(commandRepository.findRunnableByUserIdAndAssetCode(eq(1L), eq("KORI"), any()))
                .thenReturn(Optional.empty());

        OfflinePayReconcileCommandService.PollResponse response = service.poll(
                new OfflinePayReconcileCommandService.PollCommand(
                        "device-1",
                        "KORI",
                        Map.of("offlineAvailableAmount", "10.000000", "pendingCount", 1, "unsettledReceivedAmount", "2.000000")
                )
        );

        assertFalse(response.hasCommand());
        assertEquals("NO_RECONCILE_REQUIRED", response.reasonCode());
        verify(commandRepository, never()).create(anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void rejectsInactiveDevice() {
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device(DeviceStatus.REVOKED)));

        assertThrows(IllegalArgumentException.class, () -> service.poll(
                new OfflinePayReconcileCommandService.PollCommand(
                        "device-1",
                        "KORI",
                        Map.of("pendingCount", 2)
                )
        ));
    }

    private Device device(DeviceStatus status) {
        return new Device(
                "row-1",
                "device-1",
                1L,
                "public-key",
                1,
                status,
                "{}",
                now,
                now
        );
    }

    private OfflineLedgerService.HubSummaryResponse summary(String unsettled, String available, int pendingCount) {
        return new OfflineLedgerService.HubSummaryResponse(
                "device-1",
                1L,
                "KORI",
                unsettled,
                available,
                "12.000000",
                "88.000000",
                "12.000000",
                available,
                0,
                pendingCount,
                now.toString()
        );
    }

    private OfflinePayReconcileCommand command(String id, OfflinePayReconcileCommandStatus status) {
        return command(id, status, "LOCAL_COLLATERAL_SERVER_PROJECTION_GAP");
    }

    private OfflinePayReconcileCommand command(String id, OfflinePayReconcileCommandStatus status, String reasonCode) {
        return new OfflinePayReconcileCommand(
                id,
                1L,
                "KORI",
                reasonCode,
                "hub-summary:KORI:" + now,
                "nonce-1",
                status,
                now.plusMinutes(15),
                status == OfflinePayReconcileCommandStatus.DELIVERED ? "device-1" : null,
                status == OfflinePayReconcileCommandStatus.DELIVERED ? now : null,
                null,
                null,
                null,
                null,
                "{}",
                "{}",
                "{}",
                "{}",
                now,
                now
        );
    }
}
