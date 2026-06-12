package io.korion.offlinepay.interfaces.http.dto;

import io.korion.offlinepay.application.service.SettlementApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SubmitLocalEvidenceRequest(
        @NotBlank String uploaderType,
        @NotBlank String uploaderDeviceId,
        @NotEmpty List<@Valid EvidenceRequest> evidences
) {

    public SettlementApplicationService.LocalEvidenceIngestCommand toCommand(String idempotencyKey) {
        return new SettlementApplicationService.LocalEvidenceIngestCommand(
                SettlementApplicationService.UploaderType.valueOf(uploaderType),
                uploaderDeviceId,
                idempotencyKey,
                evidences.stream()
                        .map(evidence -> new SettlementApplicationService.LocalEvidenceSubmission(
                                evidence.voucherId(),
                                evidence.sessionId(),
                                evidence.direction(),
                                evidence.senderDeviceId(),
                                evidence.receiverDeviceId(),
                                evidence.amount(),
                                evidence.counter(),
                                evidence.prevHash(),
                                evidence.newHash(),
                                evidence.nonce(),
                                evidence.signature(),
                                evidence.canonicalPayload(),
                                evidence.merchantId(),
                                evidence.partnerId(),
                                evidence.leaderId(),
                                evidence.countryCode(),
                                evidence.storeId(),
                                evidence.orderId(),
                                evidence.paymentIntentId(),
                                evidence.invoiceId(),
                                evidence.fiatAmount(),
                                evidence.fiatCurrency(),
                                evidence.exchangeRate(),
                                evidence.rateTimestamp(),
                                evidence.schemaVersion(),
                                evidence.protocolVersion(),
                                evidence.hashAlgorithm(),
                                evidence.signatureAlgorithm(),
                                evidence.keyId(),
                                evidence.publicKeyFingerprint(),
                                evidence.appVersion(),
                                evidence.deviceAttestationId(),
                                evidence.deviceAttestationVerdict(),
                                evidence.serverVerifiedTrustLevel(),
                                evidence.serverAttestationVerifiedAt(),
                                evidence.transportSessionHash(),
                                evidence.transportTranscriptSource(),
                                evidence.transportTranscript(),
                                evidence.transportTranscriptEncoding(),
                                evidence.payload()
                        ))
                        .toList()
        );
    }

    public record EvidenceRequest(
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
