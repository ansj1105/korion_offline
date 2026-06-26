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
                        .map(SubmitSettlementBatchRequest::toProofSubmission)
                        .toList(),
                resolvedTriggerMode
        );
    }

    private static SettlementApplicationService.ProofSubmission toProofSubmission(ProofRequest proof) {
        Map<String, Object> spendingProof = resolveSpendingProof(proof.payload());
        return new SettlementApplicationService.ProofSubmission(
                proof.voucherId(),
                proof.collateralId(),
                proof.issuerDeviceId(),
                proof.receiverDeviceId(),
                proof.keyVersion().intValue(),
                proof.policyVersion().intValue(),
                firstLong(spendingProof.get("monotonicCounter"), spendingProof.get("counter"), proof.counter()),
                firstText(spendingProof.get("nonce"), proof.nonce()),
                firstText(spendingProof.get("newStateHash"), spendingProof.get("newHash"), proof.newHash()),
                firstText(spendingProof.get("prevStateHash"), spendingProof.get("prevHash"), proof.prevHash()),
                firstText(spendingProof.get("signature"), proof.signature()),
                proof.amount(),
                normalizeEpochMillis(firstLong(spendingProof.get("timestamp"), proof.timestamp())),
                normalizeEpochMillis(proof.expiresAt()),
                proof.canonicalPayload(),
                proof.payload()
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

    private static long normalizeEpochMillis(long value) {
        if (value > 0 && value < 10_000_000_000L) {
            return value * 1000L;
        }
        return value;
    }

    private static Map<String, Object> resolveSpendingProof(Map<String, Object> payload) {
        Map<String, Object> direct = asMap(payload.get("spendingProof"));
        if (!direct.isEmpty()) {
            return direct;
        }
        Map<String, Object> senderSignedPayload = asMap(payload.get("senderSignedPayload"));
        Map<String, Object> signed = asMap(senderSignedPayload.get("spendingProof"));
        if (!signed.isEmpty()) {
            return signed;
        }
        return asMap(payload.get("receiverLocalBlockSenderProofPayload"));
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static long firstLong(Object... values) {
        for (Object value : values) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                continue;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // Try next candidate.
            }
        }
        return 0L;
    }
}
