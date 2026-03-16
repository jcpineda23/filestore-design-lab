package com.jcpineda.filestore.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DomainEventFactory {

    public DomainEvent fileEvent(String eventType,
                                 UUID userId,
                                 UUID fileId,
                                 String correlationId,
                                 Map<String, Object> data) {
        return new DomainEvent(
            UUID.randomUUID().toString(),
            eventType,
            Instant.now(),
            userId,
            "file",
            fileId.toString(),
            correlationId,
            data
        );
    }
}
