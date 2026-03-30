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
        JsonNode payloadNode = jsonService.readTree(proof.rawPayloadJson());
        String deviceRegistrationId = textValue(payloadNode.get("deviceRegistrationId"));
        String deviceBindingKey = textValue(payloadNode.get("deviceBindingKey"));
        String authMethod = textValue(payloadNode.get("authMethod"));
        Long signedUserId = longValue(payloadNode.get("signedUserId"));

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
}
