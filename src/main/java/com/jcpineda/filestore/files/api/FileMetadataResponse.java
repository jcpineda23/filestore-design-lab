package com.jcpineda.filestore.files.api;

import com.jcpineda.filestore.files.domain.FileEntity;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FileMetadataResponse(
    UUID id,
    UUID ownerId,
    String fileName,
    String contentType,
    long sizeBytes,
    String checksum,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static FileMetadataResponse from(FileEntity entity) {
        return new FileMetadataResponse(
            entity.getId(),
            entity.getOwnerId(),
            entity.getFileName(),
            entity.getContentType(),
            entity.getSizeBytes(),
            entity.getChecksum(),
            entity.getStatus().name(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
