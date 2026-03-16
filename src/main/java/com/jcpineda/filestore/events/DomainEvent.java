package com.jcpineda.filestore.events;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent(
    String eventId,
    String eventType,
    Instant occurredAt,
    UUID userId,
    String resourceType,
    String resourceId,
    String correlationId,
    Object data
) {
}
