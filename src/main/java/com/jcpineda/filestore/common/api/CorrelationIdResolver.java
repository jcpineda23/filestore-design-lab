package com.jcpineda.filestore.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class CorrelationIdResolver {

    private CorrelationIdResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        return correlationId == null || correlationId.isBlank()
            ? UUID.randomUUID().toString()
            : correlationId;
    }
}
