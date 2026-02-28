package com.jcpineda.filestore.auth.service;

import java.util.UUID;

public record AuthUser(UUID userId, String email) {
}
