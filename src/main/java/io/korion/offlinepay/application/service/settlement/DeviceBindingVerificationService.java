package io.korion.offlinepay.application.service.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import org.springframework.stereotype.Service;

@Service
public class DeviceBindingVerificationService {

    private final JsonService jsonService;

    public DeviceBindingVerificationService(JsonService jsonService) {
        this.jsonService = jsonService;
    }

    public VerificationResult verify(Device device, OfflinePaymentProof proof) {
        JsonNode payloadNode = jsonService.readTree(proof.rawPayloadJson());
        String deviceRegistrationId = textValue(payloadNode.get("deviceRegistrationId"));
        String authMethod = textValue(payloadNode.get("authMethod"));
        Long signedUserId = longValue(payloadNode.get("signedUserId"));

        boolean hasBindingFields =
                deviceRegistrationId != null ||
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
        if (device.userId() != signedUserId) {
            return VerificationResult.invalidResult("signed user mismatch");
        }
        return VerificationResult.validResult();
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

    public record VerificationResult(boolean valid, String detail) {
        public static VerificationResult validResult() {
            return new VerificationResult(true, "valid");
        }

        public static VerificationResult invalidResult(String detail) {
            return new VerificationResult(false, detail == null ? "invalid binding" : detail);
        }
    }
}
