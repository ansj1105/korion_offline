package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class OfflinePaymentProofRowMapper implements RowMapper<OfflinePaymentProof> {

    @Override
    public OfflinePaymentProof mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new OfflinePaymentProof(
                resultSet.getString("id"),
                resultSet.getString("batch_id"),
                resultSet.getString("voucher_id"),
                resultSet.getString("collateral_id"),
                resultSet.getString("sender_device_id"),
                resultSet.getString("receiver_device_id"),
                resultSet.getInt("key_version"),
                resultSet.getInt("policy_version"),
                resultSet.getLong("counter"),
                resultSet.getString("nonce"),
                resultSet.getString("hash_chain_head"),
                resultSet.getString("previous_hash"),
                resultSet.getString("signature"),
                resultSet.getBigDecimal("amount"),
                resultSet.getLong("timestamp_ms"),
                resultSet.getLong("expires_at_ms"),
                resultSet.getString("canonical_payload"),
                resultSet.getString("uploader_type"),
                resultSet.getString("raw_payload"),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }
}
