package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SubmitClientEventBatchRequest(
        @NotBlank String deviceId,
        String assetCode,
        @NotEmpty List<@Valid ClientEventRequest> events
) {

    public record ClientEventRequest(
            @NotBlank String eventId,
            String sessionId,
            String requestId,
            String settlementId,
            String direction,
            @NotBlank String type,
            String status,
            String assetCode,
            String networkCode,
            @DecimalMin("0.00000000") BigDecimal amount,
            String counterpartyDeviceId,
            String counterpartyActor,
            String reasonCode,
            String message,
            String uploaderType,
            String uploaderDeviceId,
            @Valid ProofEventRequest proof,
            @Valid EvidenceEventRequest evidence,
            Map<String, Object> payload
    ) {}

    public record ProofEventRequest(
            @NotBlank String voucherId,
            @NotBlank String collateralId,
            @NotBlank String issuerDeviceId,
            @NotBlank String receiverDeviceId,
            @NotBlank String newHash,
            @NotBlank String prevHash,
            @NotBlank String signature,
            @NotNull @DecimalMin("0.00000001") BigDecimal amount,
            @NotNull Long keyVersion,
            @NotNull Long policyVersion,
            @NotNull Long counter,
            @NotBlank String nonce,
            @NotNull Long timestamp,
            @NotNull Long expiresAt,
            String canonicalPayload,
            @NotNull Map<String, Object> payload
    ) {}

    public record EvidenceEventRequest(
            @NotBlank String voucherId,
            String sessionId,
            @NotBlank String direction,
            @NotBlank String senderDeviceId,
            @NotBlank String receiverDeviceId,
            @NotNull @DecimalMin("0.00000001") BigDecimal amount,
            @NotNull Long counter,
            String prevHash,
            @NotBlank String newHash,
            @NotBlank String nonce,
            @NotBlank String signature,
            @NotBlank String canonicalPayload,
            String merchantId,
            String partnerId,
            String leaderId,
            String countryCode,
            String storeId,
            String orderId,
            String paymentIntentId,
            String invoiceId,
            String fiatAmount,
            String fiatCurrency,
            String exchangeRate,
            String rateTimestamp,
            String schemaVersion,
            String protocolVersion,
            String hashAlgorithm,
            String signatureAlgorithm,
            @NotBlank String keyId,
            @NotBlank String publicKeyFingerprint,
            String appVersion,
            @NotBlank String deviceAttestationId,
            @NotBlank String deviceAttestationVerdict,
            @NotBlank String serverVerifiedTrustLevel,
            @NotBlank String serverAttestationVerifiedAt,
            @NotBlank String transportSessionHash,
            @NotBlank String transportTranscriptSource,
            String transportTranscript,
            String transportTranscriptEncoding,
            @NotNull Map<String, Object> payload
    ) {}
}
