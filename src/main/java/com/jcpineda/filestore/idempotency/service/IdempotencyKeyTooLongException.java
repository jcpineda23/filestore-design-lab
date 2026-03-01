package com.jcpineda.filestore.idempotency.service;

public class IdempotencyKeyTooLongException extends RuntimeException {

    public IdempotencyKeyTooLongException(int maxLength) {
        super("Idempotency-Key must be <= " + maxLength + " characters");
    }
}
