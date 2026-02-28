package com.jcpineda.filestore.files.service;

public class InvalidFileStateException extends RuntimeException {

    public InvalidFileStateException(String message) {
        super(message);
    }
}
