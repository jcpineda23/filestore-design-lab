package com.jcpineda.filestore.auth.api;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(UUID userId, String token, Instant expiresAt) {
}
