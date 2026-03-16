package com.jcpineda.filestore.files.service;

import com.jcpineda.filestore.events.DomainEventFactory;
import com.jcpineda.filestore.events.UserEventPublisher;
import com.jcpineda.filestore.files.domain.FileEntity;
import com.jcpineda.filestore.files.domain.FileStatus;
import com.jcpineda.filestore.files.persistence.FileRepository;
import com.jcpineda.filestore.storage.ObjectStorageService;
import com.jcpineda.filestore.storage.StorageOperationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileMetadataService {

    private final FileRepository fileRepository;
    private final ObjectStorageService objectStorageService;
    private final UserEventPublisher userEventPublisher;
    private final DomainEventFactory domainEventFactory;

    public FileMetadataService(FileRepository fileRepository,
                               ObjectStorageService objectStorageService,
                               UserEventPublisher userEventPublisher,
                               DomainEventFactory domainEventFactory) {
        this.fileRepository = fileRepository;
        this.objectStorageService = objectStorageService;
        this.userEventPublisher = userEventPublisher;
        this.domainEventFactory = domainEventFactory;
    }

    @Transactional
    public FileEntity create(UUID ownerId, MultipartFile file, String correlationId) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileUploadException("Uploaded file must not be empty");
        }

        UUID fileId = UUID.randomUUID();
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
            ? "file-" + fileId
            : file.getOriginalFilename();

        String objectKey = "users/" + ownerId + "/files/" + fileId + "/" + fileName.replaceAll("\\s+", "-");

        FileEntity entity = new FileEntity(
            fileId,
            ownerId,
            fileName,
            file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
            file.getSize(),
            objectKey,
            FileStatus.UPLOADING
        );

        FileEntity saved = fileRepository.save(entity);
        publish("file.upload.started", ownerId, fileId, correlationId, Map.of(
            "fileName", saved.getFileName(),
            "sizeBytes", saved.getSizeBytes(),
            "contentType", saved.getContentType()
        ));

        try {
            var storageMetadata = objectStorageService.putObject(objectKey, file);
            saved.markReady(storageMetadata.checksum());
            FileEntity ready = fileRepository.save(saved);

            publish("file.upload.progress", ownerId, fileId, correlationId, Map.of(
                "bytesReceived", ready.getSizeBytes(),
                "sizeBytes", ready.getSizeBytes(),
                "percent", 100
            ));
            publish("file.upload.completed", ownerId, fileId, correlationId, Map.of(
                "fileName", ready.getFileName(),
                "sizeBytes", ready.getSizeBytes(),
                "checksum", storageMetadata.checksum()
            ));
            return ready;
        } catch (Exception ex) {
            saved.markFailed("STORAGE_UNAVAILABLE");
            fileRepository.save(saved);
            publish("file.upload.failed", ownerId, fileId, correlationId, Map.of(
                "reason", "STORAGE_UNAVAILABLE",
                "message", "File bytes could not be stored"
            ));
            throw new StorageUnavailableException("Could not store file bytes", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<FileEntity> list(UUID ownerId) {
        return fileRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId);
    }

    @Transactional
    public void delete(UUID ownerId, UUID fileId, String correlationId) {
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException(fileId));

        if (!file.getOwnerId().equals(ownerId)) {
            throw new FileAccessDeniedException();
        }

        if (file.getStatus() != FileStatus.READY && file.getStatus() != FileStatus.DELETE_FAILED) {
            throw new InvalidFileStateException("File cannot be deleted in state: " + file.getStatus());
        }

        file.markDeleting();
        fileRepository.save(file);

        try {
            objectStorageService.deleteObject(file.getObjectKey());
            file.markDeleted();
            fileRepository.save(file);
            publish("file.deleted", ownerId, fileId, correlationId, Map.of(
                "fileName", file.getFileName()
            ));
        } catch (Exception ex) {
            file.markDeleteFailed("STORAGE_UNAVAILABLE");
            fileRepository.save(file);
            publish("file.delete.failed", ownerId, fileId, correlationId, Map.of(
                "reason", "STORAGE_UNAVAILABLE",
                "message", "Stored file bytes could not be deleted"
            ));
            throw new StorageUnavailableException("Could not delete stored file bytes", ex);
        }
    }

    @Transactional(readOnly = true)
    public FileDownload download(UUID ownerId, UUID fileId, String correlationId) {
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException(fileId));

        if (!file.getOwnerId().equals(ownerId)) {
            throw new FileAccessDeniedException();
        }

        if (file.getStatus() != FileStatus.READY) {
            throw new InvalidFileStateException("File cannot be downloaded in state: " + file.getStatus());
        }

        try {
            var storedObject = objectStorageService.getObject(file.getObjectKey());
            publish("file.downloaded", ownerId, fileId, correlationId, Map.of(
                "fileName", file.getFileName()
            ));
            return new FileDownload(
                file.getFileName(),
                storedObject.contentType(),
                storedObject.sizeBytes(),
                storedObject.inputStream()
            );
        } catch (Exception ex) {
            throw new StorageUnavailableException("Could not load file bytes", ex);
        }
    }

    private void publish(String eventType,
                         UUID ownerId,
                         UUID fileId,
                         String correlationId,
                         Map<String, Object> data) {
        userEventPublisher.publish(domainEventFactory.fileEvent(
            eventType,
            ownerId,
            fileId,
            correlationId,
            data
        ));
    }
}
