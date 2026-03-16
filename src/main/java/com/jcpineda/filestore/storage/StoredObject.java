package com.jcpineda.filestore.storage;

import java.io.InputStream;

public record StoredObject(
    InputStream inputStream,
    String contentType,
    long sizeBytes
) {
}
