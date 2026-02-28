package com.jcpineda.filestore.files.service;

public class FileAccessDeniedException extends RuntimeException {

    public FileAccessDeniedException() {
        super("User does not own this file");
    }
}
