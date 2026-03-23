package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import io.korion.offlinepay.infrastructure.persistence.mapper.IssuedOfflineProofRowMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIssuedOfflineProofRepository implements IssuedOfflineProofRepository {

    private final JdbcClient jdbcClient;
    private final IssuedOfflineProofRowMapper rowMapper;

    public JdbcIssuedOfflineProofRepository(JdbcClient jdbcClient, IssuedOfflineProofRowMapper rowMapper) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = rowMapper;
    }

    @Override
    public IssuedOfflineProof save(
            long userId,
            String deviceId,
            String collateralId,
            String assetCode,
            BigDecimal usableAmount,
            String proofNonce,
            String issuerKeyId,
            String issuerPublicKey,
            String issuerSignature,
            String issuedPayloadJson,
            IssuedProofStatus status,
            OffsetDateTime expiresAt
    ) {
        String sql = QueryBuilder.insert(
                        "issued_offline_proofs",
                        "user_id",
                        "device_id",
                        "collateral_id",
                        "asset_code",
                        "usable_amount",
                        "proof_nonce",
                        "issuer_key_id",
                        "issuer_public_key",
                        "issuer_signature",
                        "issued_payload",
                        "status",
                        "expires_at"
                )
                .value("issued_payload", "CAST(:issuedPayload AS jsonb)")
                .build();
        jdbcClient.sql(sql)
                .param("userId", userId)
                .param("deviceId", deviceId)
                .param("collateralId", java.util.UUID.fromString(collateralId))
                .param("assetCode", assetCode)
                .param("usableAmount", usableAmount)
                .param("proofNonce", proofNonce)
                .param("issuerKeyId", issuerKeyId)
                .param("issuerPublicKey", issuerPublicKey)
                .param("issuerSignature", issuerSignature)
                .param("issuedPayload", issuedPayloadJson)
                .param("status", status.name())
                .param("expiresAt", expiresAt)
                .update();
        return findLatestActiveByUserIdAndDeviceIdAndAssetCode(userId, deviceId, assetCode).orElseThrow();
    }

    @Override
    public Optional<IssuedOfflineProof> findLatestActiveByUserIdAndDeviceIdAndAssetCode(long userId, String deviceId, String assetCode) {
        String sql = QueryBuilder.select("issued_offline_proofs")
                .where("user_id", QueryBuilder.Op.EQ, ":userId")
                .where("device_id", QueryBuilder.Op.EQ, ":deviceId")
                .where("asset_code", QueryBuilder.Op.EQ, ":assetCode")
                .where("status", QueryBuilder.Op.EQ, ":status")
                .orderBy("created_at DESC")
                .limit(1)
                .build();
        return jdbcClient.sql(sql)
                .param("userId", userId)
                .param("deviceId", deviceId)
                .param("assetCode", assetCode)
                .param("status", IssuedProofStatus.ACTIVE.name())
                .query(rowMapper)
                .optional();
    }
}
