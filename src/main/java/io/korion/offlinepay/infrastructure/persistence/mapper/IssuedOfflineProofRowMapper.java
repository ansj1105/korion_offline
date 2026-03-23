package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class IssuedOfflineProofRowMapper implements RowMapper<IssuedOfflineProof> {

    @Override
    public IssuedOfflineProof mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new IssuedOfflineProof(
                resultSet.getString("id"),
                resultSet.getLong("user_id"),
                resultSet.getString("device_id"),
                resultSet.getString("collateral_id"),
                resultSet.getString("asset_code"),
                resultSet.getBigDecimal("usable_amount"),
                resultSet.getString("proof_nonce"),
                resultSet.getString("issuer_key_id"),
                resultSet.getString("issuer_public_key"),
                resultSet.getString("issuer_signature"),
                resultSet.getString("issued_payload"),
                IssuedProofStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("consumed_by_proof_id"),
                resultSet.getObject("expires_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
