package edu.wvsu.ijwkms.audit;

import edu.wvsu.ijwkms.shared.web.CorrelationIdFilter;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
                .param("occurredAt", Instant.now(clock).atOffset(ZoneOffset.UTC))
                .update();
    }

    public PageResponse<AuditEventView> list(String action, AuditOutcome outcome, int page, int size) {
        String actionFilter = action == null ? "" : action.trim();
        String outcomeFilter = outcome == null ? "" : outcome.name();
        long total = jdbc.sql("""
                        SELECT COUNT(*)
                        FROM audit_event event
                        WHERE (:action = '' OR event.action ILIKE '%' || :action || '%')
                          AND (:outcome = '' OR event.outcome = :outcome)
                        """)
                .param("action", actionFilter)
                .param("outcome", outcomeFilter)
                .query(Long.class)
                .single();
        var events = jdbc.sql("""
                        SELECT event.id, event.actor_user_id, actor.display_name AS actor_display_name,
                               event.action, event.target_type, event.target_id, event.outcome,
                               event.correlation_id, event.occurred_at
                        FROM audit_event event
                        LEFT JOIN app_user actor ON actor.id = event.actor_user_id
                        WHERE (:action = '' OR event.action ILIKE '%' || :action || '%')
                          AND (:outcome = '' OR event.outcome = :outcome)
                        ORDER BY event.occurred_at DESC, event.id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("action", actionFilter)
                .param("outcome", outcomeFilter)
                .param("limit", size)
                .param("offset", page * size)
                .query((rs, rowNum) -> new AuditEventView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("actor_user_id", UUID.class),
                        rs.getString("actor_display_name"),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        AuditOutcome.valueOf(rs.getString("outcome")),
                        rs.getString("correlation_id"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
        return PageResponse.of(events, page, size, total);
    }

    private String toJson(Map<String, ?> detail) {
        try {
            return jsonMapper.writeValueAsString(detail);
        } catch (Exception exception) {
            throw new IllegalStateException("Audit detail could not be serialized", exception);
        }
    }
}
