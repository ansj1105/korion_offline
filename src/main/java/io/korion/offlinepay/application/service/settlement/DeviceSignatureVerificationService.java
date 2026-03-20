package io.korion.offlinepay.application.service.settlement;

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
        return proof.newStateHash() + "|" + proof.timestampMs();
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
