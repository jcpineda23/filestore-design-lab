package com.jcpineda.filestore.files.service;

import java.io.InputStream;

public record FileDownload(
    String fileName,
    String contentType,
    long sizeBytes,
    InputStream inputStream
) {
}
