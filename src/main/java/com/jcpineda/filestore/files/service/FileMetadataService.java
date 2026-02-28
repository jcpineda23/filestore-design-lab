package com.jcpineda.filestore.files.service;

import com.jcpineda.filestore.files.domain.FileEntity;
import com.jcpineda.filestore.files.domain.FileStatus;
import com.jcpineda.filestore.files.persistence.FileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileMetadataService {

    private final FileRepository fileRepository;

    public FileMetadataService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Transactional
    public FileEntity create(UUID ownerId, MultipartFile file) {
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
            FileStatus.READY
        );

        return fileRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<FileEntity> list(UUID ownerId) {
        return fileRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId);
    }

    @Transactional
    public void delete(UUID ownerId, UUID fileId) {
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException(fileId));

        if (!file.getOwnerId().equals(ownerId)) {
            throw new FileAccessDeniedException();
        }

        if (file.getStatus() != FileStatus.READY && file.getStatus() != FileStatus.DELETE_FAILED) {
            throw new InvalidFileStateException("File cannot be deleted in state: " + file.getStatus());
        }

        file.markDeleting();
        file.markDeleted();
        fileRepository.save(file);
    }
}
