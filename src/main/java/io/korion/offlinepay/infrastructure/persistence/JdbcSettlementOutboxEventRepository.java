package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.SettlementOutboxEventRepository;
import io.korion.offlinepay.domain.model.SettlementOutboxEvent;
import io.korion.offlinepay.infrastructure.persistence.mapper.SettlementOutboxEventRowMapper;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSettlementOutboxEventRepository implements SettlementOutboxEventRepository {

    private final JdbcClient jdbcClient;
    private final SettlementOutboxEventRowMapper rowMapper;

    public JdbcSettlementOutboxEventRepository(JdbcClient jdbcClient, SettlementOutboxEventRowMapper rowMapper) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = rowMapper;
    }

    @Override
    public List<SettlementOutboxEvent> findRecent(int limit, String eventType, String status) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("settlement_outbox_events");
        if (eventType != null && !eventType.isBlank()) {
            builder.where("event_type", QueryBuilder.Op.EQ, ":eventType");
        }
        if (status != null && !status.isBlank()) {
            builder.where("status", QueryBuilder.Op.EQ, ":status");
        }
        String sql = builder.orderBy("created_at DESC").limit(limit).build();
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql);
        if (eventType != null && !eventType.isBlank()) {
            statement.param("eventType", eventType);
        }
        if (status != null && !status.isBlank()) {
            statement.param("status", status);
        }
        return statement.query(rowMapper).list();
    }

    @Override
    public SettlementOutboxEvent findById(String id) {
        String sql = QueryBuilder.select("settlement_outbox_events")
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        return jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(id))
                .query(rowMapper)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("settlement outbox event not found: " + id));
    }

    @Override
    public SettlementOutboxEvent requeueDeadLetter(String id) {
        String sql = QueryBuilder.update("settlement_outbox_events")
                .set("status", ":pendingStatus")
                .set("lock_owner", QueryBuilder.Op.ASSIGN, "NULL")
                .set("locked_at", QueryBuilder.Op.ASSIGN, "NULL")
                .set("processed_at", QueryBuilder.Op.ASSIGN, "NULL")
                .set("dead_lettered_at", QueryBuilder.Op.ASSIGN, "NULL")
                .set("reason_code", QueryBuilder.Op.ASSIGN, "NULL")
                .set("error_message", QueryBuilder.Op.ASSIGN, "NULL")
                .where("id", QueryBuilder.Op.EQ, ":id")
                .where("status", QueryBuilder.Op.EQ, ":deadLetterStatus")
                .build();
        int updated = jdbcClient.sql(sql)
                .param("pendingStatus", "PENDING")
                .param("deadLetterStatus", "DEAD_LETTER")
                .param("id", java.util.UUID.fromString(id))
                .update();
        if (updated <= 0) {
            throw new IllegalArgumentException("dead-letter settlement outbox event not found: " + id);
        }
        return findById(id);
    }

    @Override
    public long countByStatus(String status) {
        String sql = QueryBuilder.select("settlement_outbox_events", "COUNT(*)")
                .where("status", QueryBuilder.Op.EQ, ":status")
                .build();
        Long count = jdbcClient.sql(sql)
                .param("status", status)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    @Override
    public long countByEventType(String eventType) {
        String sql = QueryBuilder.select("settlement_outbox_events", "COUNT(*)")
                .where("event_type", QueryBuilder.Op.EQ, ":eventType")
                .build();
        Long count = jdbcClient.sql(sql)
                .param("eventType", eventType)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }
}
