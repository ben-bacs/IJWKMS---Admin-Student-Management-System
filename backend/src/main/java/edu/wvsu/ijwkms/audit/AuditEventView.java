package edu.wvsu.ijwkms.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventView(
        UUID id,
        UUID actorUserId,
        String actorDisplayName,
        String action,
        String targetType,
        String targetId,
        AuditOutcome outcome,
        String correlationId,
        Instant occurredAt) {}
