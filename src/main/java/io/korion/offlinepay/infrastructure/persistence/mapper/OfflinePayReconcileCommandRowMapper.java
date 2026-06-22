package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.OfflinePayReconcileCommand;
import io.korion.offlinepay.domain.status.OfflinePayReconcileCommandStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class OfflinePayReconcileCommandRowMapper implements RowMapper<OfflinePayReconcileCommand> {

    @Override
    public OfflinePayReconcileCommand mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OfflinePayReconcileCommand(
                rs.getObject("id").toString(),
                rs.getLong("user_id"),
                rs.getString("asset_code"),
                rs.getString("reason_code"),
                rs.getString("projection_version"),
                rs.getString("nonce"),
                OfflinePayReconcileCommandStatus.valueOf(rs.getString("status")),
                rs.getObject("expires_at", java.time.OffsetDateTime.class),
                rs.getString("delivered_to_device_id"),
                rs.getObject("delivered_at", java.time.OffsetDateTime.class),
                rs.getString("applied_by_device_id"),
                rs.getObject("applied_at", java.time.OffsetDateTime.class),
                rs.getObject("failed_at", java.time.OffsetDateTime.class),
                rs.getString("error_message"),
                rs.getString("dry_run_summary"),
                rs.getString("apply_summary"),
                rs.getString("local_summary"),
                rs.getString("metadata"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
