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
}
