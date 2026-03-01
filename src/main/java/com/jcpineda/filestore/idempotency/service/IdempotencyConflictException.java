package com.jcpineda.filestore.idempotency.service;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key conflict for key: " + idempotencyKey);
    }
}
