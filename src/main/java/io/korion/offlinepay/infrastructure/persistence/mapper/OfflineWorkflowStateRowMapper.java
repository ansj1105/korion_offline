package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.OfflineWorkflowState;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class OfflineWorkflowStateRowMapper implements RowMapper<OfflineWorkflowState> {

    @Override
    public OfflineWorkflowState mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OfflineWorkflowState(
                rs.getObject("id").toString(),
                rs.getString("workflow_type"),
                rs.getString("workflow_id"),
                rs.getString("workflow_stage"),
                rs.getString("event_type"),
                rs.getObject("source_event_id").toString(),
                rs.getObject("batch_id") == null ? null : rs.getObject("batch_id").toString(),
                rs.getString("settlement_id"),
                rs.getObject("operation_id") == null ? null : rs.getObject("operation_id").toString(),
                rs.getString("proof_id"),
                rs.getString("reference_id"),
                rs.getString("asset_code"),
                rs.getString("reason_code"),
                rs.getString("error_message"),
                rs.getString("payload_json"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
