package io.korion.offlinepay.application.service;

import io.korion.offlinepay.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class ProofIssuerSignatureService {

    private final String keyId;
    private final String publicKeyBase64;
    private final PrivateKey privateKey;

    public ProofIssuerSignatureService(AppProperties properties) {
        try {
            AppProperties.ProofIssuer issuer = properties.proofIssuer();
            if (issuer != null
                    && issuer.privateKey() != null && !issuer.privateKey().isBlank()
                    && issuer.publicKey() != null && !issuer.publicKey().isBlank()) {
                this.keyId = issuer.keyId() == null || issuer.keyId().isBlank() ? "configured-proof-issuer" : issuer.keyId().trim();
                this.publicKeyBase64 = issuer.publicKey().trim();
                byte[] privateKeyBytes = Base64.getDecoder().decode(issuer.privateKey().trim().replaceAll("\\s+", ""));
                this.privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
                return;
            }

            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair generated = generator.generateKeyPair();
            this.keyId = "ephemeral-proof-issuer";
            this.publicKeyBase64 = Base64.getEncoder().encodeToString(generated.getPublic().getEncoded());
            this.privateKey = generated.getPrivate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize proof issuer signature service", exception);
        }
    }

    public String sign(String payload) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign issued proof payload", exception);
        }
    }

    public boolean verify(String payload, String publicKeyBase64, String signatureBase64) {
        try {
            if (payload == null || payload.isBlank()
                    || publicKeyBase64 == null || publicKeyBase64.isBlank()
                    || signatureBase64 == null || signatureBase64.isBlank()) {
                return false;
            }
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64.trim().replaceAll("\\s+", ""));
            PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(publicKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64.trim().replaceAll("\\s+", ""));
            return signature.verify(signatureBytes);
        } catch (Exception ignored) {
            return false;
        }
    }

    public String keyId() {
        return keyId;
    }

    public String publicKey() {
        return publicKeyBase64;
    }
}
