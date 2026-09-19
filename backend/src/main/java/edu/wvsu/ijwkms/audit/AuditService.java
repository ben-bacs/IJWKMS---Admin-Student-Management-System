package edu.wvsu.ijwkms.audit;

import edu.wvsu.ijwkms.shared.web.CorrelationIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditService(JdbcClient jdbc, JsonMapper jsonMapper, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public void record(
            UUID actorUserId,
            String action,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            Map<String, ?> detail) {
        jdbc.sql("""
                        INSERT INTO audit_event (
                            id, actor_user_id, action, target_type, target_id,
                            outcome, correlation_id, detail, occurred_at
                        ) VALUES (
                            :id, :actorUserId, :action, :targetType, :targetId,
                            :outcome, :correlationId, CAST(:detail AS jsonb), :occurredAt
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("actorUserId", actorUserId)
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("outcome", outcome.name())
                .param("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY))
                .param("detail", toJson(detail))
                .param("occurredAt", Instant.now(clock))
                .update();
    }

    private String toJson(Map<String, ?> detail) {
        try {
            return jsonMapper.writeValueAsString(detail);
        } catch (Exception exception) {
            throw new IllegalStateException("Audit detail could not be serialized", exception);
        }
    }
}
