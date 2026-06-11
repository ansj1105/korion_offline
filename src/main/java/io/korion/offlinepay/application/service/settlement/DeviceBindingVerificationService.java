package io.korion.offlinepay.application.service.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Service;

@Service
public class DeviceBindingVerificationService {

    private static final String DEVICE_BINDING_NAMESPACE = "korion-offline-pay:device-binding";

    private final JsonService jsonService;

    public DeviceBindingVerificationService(JsonService jsonService) {
        this.jsonService = jsonService;
    }

    public VerificationResult verify(Device device, OfflinePaymentProof proof) {
        JsonNode payloadNode = readPayload(proof.rawPayloadJson());
        JsonNode canonicalPayloadNode = readPayload(proof.canonicalPayload());
        FieldResolution deviceRegistrationIdField = resolveTextField("deviceRegistrationId", payloadNode, canonicalPayloadNode);
        FieldResolution authMethodField = resolveTextField("authMethod", payloadNode, canonicalPayloadNode);
        FieldResolution signedUserIdField = resolveTextField("signedUserId", payloadNode, canonicalPayloadNode);
        FieldResolution deviceBindingKeyField = resolveTopLevelTextField("deviceBindingKey", payloadNode, canonicalPayloadNode);
        String fieldError = firstError(deviceRegistrationIdField, authMethodField, signedUserIdField, deviceBindingKeyField);
        if (fieldError != null) {
            return VerificationResult.invalidResult(fieldError);
        }
        String deviceRegistrationId = deviceRegistrationIdField.value();
        String deviceBindingKey = deviceBindingKeyField.value();
        String authMethod = authMethodField.value();
        Long signedUserId = longValue(signedUserIdField.value());

        boolean hasBindingFields =
                deviceRegistrationId != null ||
                deviceBindingKey != null ||
                authMethod != null ||
                signedUserId != null;

        if (!hasBindingFields) {
            return VerificationResult.validResult();
        }
        if (deviceRegistrationId == null || authMethod == null || signedUserId == null) {
            return VerificationResult.invalidResult("incomplete binding payload");
        }
        if (!device.id().equals(deviceRegistrationId)) {
            return VerificationResult.invalidResult("device registration id mismatch");
        }
        if (deviceBindingKey != null && !deviceBindingKey.equals(buildDeviceBindingKey(device))) {
            return VerificationResult.invalidResult("device binding key mismatch");
        }
        if (device.userId() != signedUserId) {
            return VerificationResult.invalidResult("signed user mismatch");
        }
        return VerificationResult.validResult();
    }

    public static String buildDeviceBindingKey(Device device) {
        return sha256Hex(String.join(
                "|",
                DEVICE_BINDING_NAMESPACE,
                device.id(),
                device.publicKey(),
                String.valueOf(device.keyVersion())
        ));
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private JsonNode readPayload(String payload) {
        return jsonService.readTree(payload == null || payload.isBlank() ? "{}" : payload);
    }

    private FieldResolution resolveTextField(String fieldName, JsonNode rawPayload, JsonNode canonicalPayload) {
        return resolveCandidates(fieldName, new FieldCandidate[] {
                candidate(fieldName, "raw", rawPayload),
                candidate(fieldName, "raw.spendingProof", rawPayload.path("spendingProof")),
                candidate(fieldName, "canonical", canonicalPayload),
                candidate(fieldName, "canonical.spendingProof", canonicalPayload.path("spendingProof"))
        });
    }

    private FieldResolution resolveTopLevelTextField(String fieldName, JsonNode rawPayload, JsonNode canonicalPayload) {
        return resolveCandidates(fieldName, new FieldCandidate[] {
                candidate(fieldName, "raw", rawPayload),
                candidate(fieldName, "canonical", canonicalPayload)
        });
    }

    private FieldResolution resolveCandidates(String fieldName, FieldCandidate[] candidates) {
        String value = null;
        String source = "";
        for (FieldCandidate candidate : candidates) {
            if (candidate.value() == null) {
                continue;
            }
            if (value == null) {
                value = candidate.value();
                source = candidate.source();
                continue;
            }
            if (!value.equals(candidate.value())) {
                return new FieldResolution(null, fieldName + " mismatch between " + source + " and " + candidate.source());
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

    private Long longValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.canConvertToLong()) {
            return node.asLong();
        }
        String text = textValue(node);
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long longValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    public record VerificationResult(boolean valid, String detail) {
        public static VerificationResult validResult() {
            return new VerificationResult(true, "valid");
        }

        public static VerificationResult invalidResult(String detail) {
            return new VerificationResult(false, detail == null ? "invalid binding" : detail);
        }
    }

    private record FieldResolution(String value, String error) {}

    private record FieldCandidate(String source, String value) {}
}
