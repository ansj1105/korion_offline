package io.korion.offlinepay.application.service.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
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
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(signingPayload(proof).getBytes(StandardCharsets.UTF_8));
            boolean verified = verifier.verify(Base64.getDecoder().decode(normalizeBase64(proof.signature())));
            return verified
                    ? VerificationResult.verifiedResult()
                    : VerificationResult.invalidResult("signature mismatch");
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

    private String signingPayload(OfflinePaymentProof proof) {
        String deviceRegistrationId = "";
        String signedUserId = "";
        String authMethod = "";
        try {
            JsonNode payloadNode = objectMapper.readTree(
                    proof.rawPayloadJson() == null || proof.rawPayloadJson().isBlank() ? "{}" : proof.rawPayloadJson()
            );
            deviceRegistrationId = textValue(payloadNode.get("deviceRegistrationId"));
            signedUserId = textValue(payloadNode.get("signedUserId"));
            authMethod = textValue(payloadNode.get("authMethod"));
        } catch (Exception ignored) {
            // Keep backward compatibility for old payloads with no binding context.
        }
        return proof.newStateHash() + "|" + proof.timestampMs() + "|" + deviceRegistrationId + "|" + signedUserId + "|" + authMethod;
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
