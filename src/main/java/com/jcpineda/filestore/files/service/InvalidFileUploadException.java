package com.jcpineda.filestore.files.service;

public class InvalidFileUploadException extends RuntimeException {

    public InvalidFileUploadException(String message) {
        super(message);
    }
}
