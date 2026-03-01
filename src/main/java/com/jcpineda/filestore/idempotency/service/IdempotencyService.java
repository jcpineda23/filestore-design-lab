package com.jcpineda.filestore.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcpineda.filestore.idempotency.domain.IdempotencyKeyEntity;
import com.jcpineda.filestore.idempotency.persistence.IdempotencyKeyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository, ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public <T> Optional<IdempotencyReplay<T>> tryReplay(UUID ownerId,
                                                         String idempotencyKey,
                                                         String requestHash,
                                                         Class<T> responseType) {
        validateKeyLength(idempotencyKey);

        Optional<IdempotencyKeyEntity> existing = idempotencyKeyRepository
            .findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey);

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyKeyEntity entity = existing.get();
        if (!entity.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }

        try {
            T body = objectMapper.readValue(entity.getResponseBody(), responseType);
            return Optional.of(new IdempotencyReplay<>(entity.getResponseCode(), body));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored idempotency response body is invalid JSON", ex);
        }
    }

    @Transactional
    public void save(UUID ownerId,
                     String idempotencyKey,
                     String requestHash,
                     int responseCode,
                     Object responseBody) {
        validateKeyLength(idempotencyKey);

        try {
            String body = objectMapper.writeValueAsString(responseBody);
            IdempotencyKeyEntity entity = new IdempotencyKeyEntity(
                UUID.randomUUID(),
                ownerId,
                idempotencyKey,
                requestHash,
                responseCode,
                body
            );
            idempotencyKeyRepository.save(entity);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize idempotency response body", ex);
        } catch (DataIntegrityViolationException ex) {
            throw new IdempotencyConflictException(idempotencyKey);
        }
    }

    private void validateKeyLength(String idempotencyKey) {
        if (idempotencyKey != null && idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IdempotencyKeyTooLongException(MAX_IDEMPOTENCY_KEY_LENGTH);
        }
    }

    public record IdempotencyReplay<T>(int responseCode, T responseBody) {
    }
}
