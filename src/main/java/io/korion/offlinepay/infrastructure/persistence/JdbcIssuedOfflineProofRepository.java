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
            String proofId,
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
                        "id",
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
                .param("id", java.util.UUID.fromString(proofId))
                .param("user_id", userId)
                .param("device_id", deviceId)
                .param("collateral_id", java.util.UUID.fromString(collateralId))
                .param("asset_code", assetCode)
                .param("usable_amount", usableAmount)
                .param("proof_nonce", proofNonce)
                .param("issuer_key_id", issuerKeyId)
                .param("issuer_public_key", issuerPublicKey)
                .param("issuer_signature", issuerSignature)
                .param("issuedPayload", issuedPayloadJson)
                .param("status", status.name())
                .param("expires_at", expiresAt)
                .update();
        return findById(proofId).orElseThrow();
    }

    @Override
    public Optional<IssuedOfflineProof> findById(String proofId) {
        String sql = QueryBuilder.select("issued_offline_proofs")
                .where("id", QueryBuilder.Op.EQ, ":id")
                .limit(1)
                .build();
        return jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(proofId))
                .query(rowMapper)
                .optional();
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

    @Override
    public boolean existsActiveByCollateralId(String collateralId) {
        String sql = QueryBuilder.select("issued_offline_proofs", "COUNT(*)")
                .where("collateral_id", QueryBuilder.Op.EQ, "CAST(:collateralId AS uuid)")
                .where("status", QueryBuilder.Op.EQ, ":status")
                .build();
        Integer count = jdbcClient.sql(sql)
                .param("collateralId", collateralId)
                .param("status", IssuedProofStatus.ACTIVE.name())
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public void updateStatus(String proofId, IssuedProofStatus status, String consumedByProofId) {
        String sql = QueryBuilder.update("issued_offline_proofs")
                .set("status", ":status")
                .set("consumed_by_proof_id", consumedByProofId == null ? "NULL" : "CAST(:consumedByProofId AS uuid)")
                .set("updated_at", "NOW()")
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(proofId))
                .param("status", status.name());
        if (consumedByProofId != null) {
            spec.param("consumedByProofId", consumedByProofId);
        }
        spec.update();
    }
}
