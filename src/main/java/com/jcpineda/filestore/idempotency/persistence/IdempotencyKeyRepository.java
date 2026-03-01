package com.jcpineda.filestore.idempotency.persistence;

import com.jcpineda.filestore.idempotency.domain.IdempotencyKeyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    Optional<IdempotencyKeyEntity> findByOwnerIdAndIdempotencyKey(UUID ownerId, String idempotencyKey);
}
