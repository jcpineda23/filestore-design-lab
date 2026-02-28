package com.jcpineda.filestore.files.service;

import java.util.UUID;

public class FileNotFoundException extends RuntimeException {

    public FileNotFoundException(UUID fileId) {
        super("File not found: " + fileId);
    }
}
