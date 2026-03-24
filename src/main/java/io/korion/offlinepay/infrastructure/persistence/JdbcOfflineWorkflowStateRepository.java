package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.OfflineWorkflowStateRepository;
import io.korion.offlinepay.domain.model.OfflineWorkflowState;
import io.korion.offlinepay.infrastructure.persistence.mapper.OfflineWorkflowStateRowMapper;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOfflineWorkflowStateRepository implements OfflineWorkflowStateRepository {

    private final JdbcClient jdbcClient;
    private final OfflineWorkflowStateRowMapper rowMapper;

    public JdbcOfflineWorkflowStateRepository(JdbcClient jdbcClient, OfflineWorkflowStateRowMapper rowMapper) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = rowMapper;
    }

    @Override
    public OfflineWorkflowState upsert(
            String workflowType,
            String workflowId,
            String workflowStage,
            String eventType,
            String sourceEventId,
            String batchId,
            String settlementId,
            String operationId,
            String proofId,
            String referenceId,
            String assetCode,
            String reasonCode,
            String errorMessage,
            String payloadJson
    ) {
        String sql = """
                INSERT INTO offline_workflow_states (
                    workflow_type,
                    workflow_id,
                    workflow_stage,
                    event_type,
                    source_event_id,
                    batch_id,
                    settlement_id,
                    operation_id,
                    proof_id,
                    reference_id,
                    asset_code,
                    reason_code,
                    error_message,
                    payload_json
                ) VALUES (
                    :workflowType,
                    :workflowId,
                    :workflowStage,
                    :eventType,
                    :sourceEventId,
                    :batchId,
                    :settlementId,
                    :operationId,
                    :proofId,
                    :referenceId,
                    :assetCode,
                    :reasonCode,
                    :errorMessage,
                    CAST(:payloadJson AS jsonb)
                )
                ON CONFLICT (workflow_type, workflow_id) DO UPDATE
                SET workflow_stage = EXCLUDED.workflow_stage,
                    event_type = EXCLUDED.event_type,
                    source_event_id = EXCLUDED.source_event_id,
                    batch_id = EXCLUDED.batch_id,
                    settlement_id = EXCLUDED.settlement_id,
                    operation_id = EXCLUDED.operation_id,
                    proof_id = EXCLUDED.proof_id,
                    reference_id = EXCLUDED.reference_id,
                    asset_code = EXCLUDED.asset_code,
                    reason_code = EXCLUDED.reason_code,
                    error_message = EXCLUDED.error_message,
                    payload_json = EXCLUDED.payload_json,
                    updated_at = NOW()
                RETURNING *
                """;
        return jdbcClient.sql(sql)
                .param("workflowType", workflowType)
                .param("workflowId", workflowId)
                .param("workflowStage", workflowStage)
                .param("eventType", eventType)
                .param("sourceEventId", java.util.UUID.fromString(sourceEventId))
                .param("batchId", batchId == null || batchId.isBlank() ? null : java.util.UUID.fromString(batchId))
                .param("settlementId", settlementId)
                .param("operationId", operationId == null || operationId.isBlank() ? null : java.util.UUID.fromString(operationId))
                .param("proofId", proofId)
                .param("referenceId", referenceId)
                .param("assetCode", assetCode)
                .param("reasonCode", reasonCode)
                .param("errorMessage", errorMessage)
                .param("payloadJson", payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson)
                .query(rowMapper)
                .single();
    }

    @Override
    public List<OfflineWorkflowState> findRecent(int limit, String workflowType, String workflowStage) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("offline_workflow_states");
        if (workflowType != null && !workflowType.isBlank()) {
            builder.where("workflow_type", QueryBuilder.Op.EQ, ":workflowType");
        }
        if (workflowStage != null && !workflowStage.isBlank()) {
            builder.where("workflow_stage", QueryBuilder.Op.EQ, ":workflowStage");
        }
        String sql = builder.orderBy("updated_at DESC").limit(limit).build();
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql);
        if (workflowType != null && !workflowType.isBlank()) {
            statement.param("workflowType", workflowType);
        }
        if (workflowStage != null && !workflowStage.isBlank()) {
            statement.param("workflowStage", workflowStage);
        }
        return statement.query(rowMapper).list();
    }
}
