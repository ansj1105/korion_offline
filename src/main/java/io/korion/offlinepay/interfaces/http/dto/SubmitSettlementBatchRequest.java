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

public record SubmitSettlementBatchRequest(
        @NotBlank String uploaderType,
        @NotBlank String uploaderDeviceId,
        @NotEmpty List<@Valid ProofRequest> proofs,
        String triggerMode
) {

    public SettlementApplicationService.SubmitSettlementBatchCommand toCommand(String idempotencyKey) {
        String resolvedTriggerMode = (triggerMode != null && !triggerMode.isBlank())
                ? triggerMode.toUpperCase()
                : "MANUAL";
        return new SettlementApplicationService.SubmitSettlementBatchCommand(
                SettlementApplicationService.UploaderType.valueOf(uploaderType),
                uploaderDeviceId,
                idempotencyKey,
                proofs.stream()
                        .map(proof -> new SettlementApplicationService.ProofSubmission(
                                proof.voucherId(),
                                proof.collateralId(),
                                proof.issuerDeviceId(),
                                proof.receiverDeviceId(),
                                proof.keyVersion().intValue(),
                                proof.policyVersion().intValue(),
                                proof.counter(),
                                proof.nonce(),
                                proof.newHash(),
                                proof.prevHash(),
                                proof.signature(),
                                proof.amount(),
                                proof.timestamp(),
                                proof.expiresAt(),
                                proof.canonicalPayload(),
                                proof.payload()
                        ))
                        .toList(),
                resolvedTriggerMode
        );
    }

    public record ProofRequest(
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
}
