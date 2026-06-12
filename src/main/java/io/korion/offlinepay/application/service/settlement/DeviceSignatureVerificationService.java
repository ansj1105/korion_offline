package io.korion.offlinepay.application.service.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class DeviceSignatureVerificationService {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public VerificationResult verify(Device device, OfflinePaymentProof proof) {
        if (proof.signature() == null || proof.signature().isBlank()) {
            return VerificationResult.invalidResult("missing signature");
        }
        if (proof.signature().startsWith("local_sig_")) {
            return VerificationResult.unsupportedResult("web fallback signature");
        }
        if (device.publicKey() == null || device.publicKey().isBlank()) {
            return VerificationResult.unsupportedResult("device public key missing");
        }

        try {
            PublicKey publicKey = parseEcPublicKey(device.publicKey());
            BindingContext bindingContext = resolveBindingContext(proof);
            if (bindingContext.error() != null) {
                return VerificationResult.invalidResult(bindingContext.error());
            }
            String signingPayload = signingPayload(proof, bindingContext);
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(signingPayload.getBytes(StandardCharsets.UTF_8));
            boolean verified = verifier.verify(Base64.getDecoder().decode(normalizeBase64(proof.signature())));
            return verified
                    ? VerificationResult.verifiedResult()
                    : VerificationResult.invalidResult(signatureMismatchDetail(proof, bindingContext, signingPayload));
        } catch (IllegalArgumentException exception) {
            return VerificationResult.invalidResult("invalid signature encoding");
        } catch (Exception exception) {
            return VerificationResult.unsupportedResult(exception.getMessage());
        }
    }

    public VerificationResult verifyPayload(Device device, String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return VerificationResult.invalidResult("missing signature");
        }
        if (signature.startsWith("local_sig_") || signature.startsWith("local_block_sig_")) {
            return VerificationResult.unsupportedResult("web fallback signature");
        }
        if (payload == null || payload.isBlank()) {
            return VerificationResult.invalidResult("missing signing payload");
        }
        if (device.publicKey() == null || device.publicKey().isBlank()) {
            return VerificationResult.unsupportedResult("device public key missing");
        }

        try {
            PublicKey publicKey = parseEcPublicKey(device.publicKey());
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            boolean verified = verifier.verify(Base64.getDecoder().decode(normalizeBase64(signature)));
            return verified
                    ? VerificationResult.verifiedResult()
                    : VerificationResult.invalidResult(
                            "signature mismatch; signingPayloadHash=" + signingPayloadHashForVerification(payload)
                    );
        } catch (IllegalArgumentException exception) {
            return VerificationResult.invalidResult("invalid signature encoding");
        } catch (Exception exception) {
            return VerificationResult.unsupportedResult(exception.getMessage());
        }
    }

    private PublicKey parseEcPublicKey(String encodedPublicKey) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(normalizeBase64(stripPem(encodedPublicKey)));
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(bytes);
        return KeyFactory.getInstance("EC").generatePublic(keySpec);
    }

    private String signingPayload(OfflinePaymentProof proof, BindingContext bindingContext) {
        return buildSigningPayloadForVerification(
                proof.newStateHash(),
                proof.timestampMs(),
                bindingContext.deviceRegistrationId(),
                bindingContext.signedUserId(),
                bindingContext.authMethod()
        );
    }

    static String buildSigningPayloadForVerification(
            String newStateHash,
            long timestampMs,
            String deviceRegistrationId,
            String signedUserId,
            String authMethod
    ) {
        return safe(newStateHash) + "|" + timestampMs + "|"
                + safeBinding(deviceRegistrationId) + "|"
                + safeBinding(signedUserId) + "|"
                + safeBinding(authMethod);
    }

    static String signingPayloadHashForVerification(String signingPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safe(signingPayload).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private String signatureMismatchDetail(
            OfflinePaymentProof proof,
            BindingContext bindingContext,
            String signingPayload
    ) {
        return "signature mismatch"
                + "; signingPayloadHash=" + signingPayloadHashForVerification(signingPayload)
                + "; newStateHash=" + safe(proof.newStateHash())
                + "; timestamp=" + proof.timestampMs()
                + "; deviceRegistrationId=" + safeBinding(bindingContext.deviceRegistrationId())
                + "; signedUserId=" + safeBinding(bindingContext.signedUserId())
                + "; authMethod=" + safeBinding(bindingContext.authMethod());
    }

    private BindingContext resolveBindingContext(OfflinePaymentProof proof) {
        try {
            JsonNode rawPayload = readPayload(proof.rawPayloadJson());
            JsonNode canonicalPayload = readPayload(proof.canonicalPayload());
            FieldResolution deviceRegistrationId = resolveField("deviceRegistrationId", rawPayload, canonicalPayload);
            FieldResolution signedUserId = resolveField("signedUserId", rawPayload, canonicalPayload);
            FieldResolution authMethod = resolveField("authMethod", rawPayload, canonicalPayload);
            String error = firstError(deviceRegistrationId, signedUserId, authMethod);
            if (error != null) {
                return new BindingContext("", "", "", error);
            }
            return new BindingContext(deviceRegistrationId.value(), signedUserId.value(), authMethod.value(), null);
        } catch (Exception exception) {
            return new BindingContext("", "", "", "invalid signature payload context");
        }
    }

    private JsonNode readPayload(String payload) throws Exception {
        return objectMapper.readTree(payload == null || payload.isBlank() ? "{}" : payload);
    }

    private FieldResolution resolveField(String fieldName, JsonNode rawPayload, JsonNode canonicalPayload) {
        String value = "";
        String source = "";
        for (FieldCandidate candidate : new FieldCandidate[] {
                candidate(fieldName, "raw", rawPayload),
                candidate(fieldName, "raw.spendingProof", rawPayload.path("spendingProof")),
                candidate(fieldName, "canonical", canonicalPayload),
                candidate(fieldName, "canonical.spendingProof", canonicalPayload.path("spendingProof"))
        }) {
            if (candidate.value() == null || candidate.value().isBlank()) {
                continue;
            }
            if (value.isBlank()) {
                value = candidate.value();
                source = candidate.source();
                continue;
            }
            if (!value.equals(candidate.value())) {
                return new FieldResolution("", fieldName + " mismatch between " + source + " and " + candidate.source());
            }
        }
        return new FieldResolution(value, null);
    }

    private FieldCandidate candidate(String fieldName, String source, JsonNode node) {
        return new FieldCandidate(source, textValue(node == null || node.isMissingNode() ? null : node.get(fieldName)));
    }

    private String firstError(FieldResolution... resolutions) {
        for (FieldResolution resolution : resolutions) {
            if (resolution.error() != null) {
                return resolution.error();
            }
        }
        return null;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        String text = node.asText("");
        return text == null ? "" : text.trim();
    }

    private String stripPem(String value) {
        return value
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    private String normalizeBase64(String value) {
        return value.replaceAll("\\s+", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeBinding(String value) {
        return value == null ? "" : value.trim();
    }

    private record BindingContext(String deviceRegistrationId, String signedUserId, String authMethod, String error) {}

    private record FieldResolution(String value, String error) {}

    private record FieldCandidate(String source, String value) {}

    public record VerificationResult(
            boolean verified,
            boolean unsupported,
            String detail
    ) {
        public static VerificationResult verifiedResult() {
            return new VerificationResult(true, false, "verified");
        }

        public static VerificationResult unsupportedResult(String detail) {
            return new VerificationResult(false, true, detail == null ? "unsupported" : detail);
        }

        public static VerificationResult invalidResult(String detail) {
            return new VerificationResult(false, false, detail == null ? "invalid" : detail);
        }
    }
}
