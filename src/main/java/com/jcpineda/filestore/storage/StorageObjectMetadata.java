package com.jcpineda.filestore.storage;

public record StorageObjectMetadata(
    String objectKey,
    String checksum,
    long sizeBytes,
    String contentType
) {
}
