package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.OfflineSagaRepository;
import io.korion.offlinepay.domain.model.OfflineSaga;
import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import io.korion.offlinepay.infrastructure.persistence.mapper.OfflineSagaRowMapper;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOfflineSagaRepository implements OfflineSagaRepository {

    private final JdbcClient jdbcClient;
    private final OfflineSagaRowMapper rowMapper;

    public JdbcOfflineSagaRepository(JdbcClient jdbcClient, OfflineSagaRowMapper rowMapper) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = rowMapper;
    }

    @Override
    public OfflineSaga saveOrReplace(
            OfflineSagaType sagaType,
            String referenceId,
            OfflineSagaStatus status,
            String currentStep,
            String lastReasonCode,
            String payloadJson
    ) {
        String sql = """
                INSERT INTO offline_sagas (
                    saga_type, reference_id, status, current_step, last_reason_code, payload_json
                ) VALUES (
                    :sagaType, :referenceId, :status, :currentStep, :lastReasonCode, CAST(:payloadJson AS jsonb)
                )
                ON CONFLICT (saga_type, reference_id) DO UPDATE
                SET status = EXCLUDED.status,
                    current_step = EXCLUDED.current_step,
                    last_reason_code = EXCLUDED.last_reason_code,
                    payload_json = EXCLUDED.payload_json,
                    updated_at = NOW()
                RETURNING *
                """;
        return jdbcClient.sql(sql)
                .param("sagaType", sagaType.name())
                .param("referenceId", referenceId)
                .param("status", status.name())
                .param("currentStep", currentStep)
                .param("lastReasonCode", lastReasonCode)
                .param("payloadJson", payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson)
                .query(rowMapper)
                .single();
    }

    @Override
    public OfflineSaga updateStatus(
            OfflineSagaType sagaType,
            String referenceId,
            OfflineSagaStatus status,
            String currentStep,
            String lastReasonCode,
            String payloadJson
    ) {
        return saveOrReplace(sagaType, referenceId, status, currentStep, lastReasonCode, payloadJson);
    }

    @Override
    public List<OfflineSaga> findRecent(int limit, OfflineSagaType sagaType, OfflineSagaStatus status) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("offline_sagas");
        if (sagaType != null) {
            builder.where("saga_type", QueryBuilder.Op.EQ, ":sagaType");
        }
        if (status != null) {
            builder.where("status", QueryBuilder.Op.EQ, ":status");
        }
        String sql = builder.orderBy("updated_at DESC").limit(limit).build();
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql);
        if (sagaType != null) {
            statement.param("sagaType", sagaType.name());
        }
        if (status != null) {
            statement.param("status", status.name());
        }
        return statement.query(rowMapper).list();
    }
}
