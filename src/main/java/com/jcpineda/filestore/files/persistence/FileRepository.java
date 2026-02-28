package com.jcpineda.filestore.files.persistence;

import com.jcpineda.filestore.files.domain.FileEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<FileEntity, UUID> {

    List<FileEntity> findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerId);

    Optional<FileEntity> findByIdAndDeletedAtIsNull(UUID id);
}
