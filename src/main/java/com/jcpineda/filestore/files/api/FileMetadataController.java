package com.jcpineda.filestore.files.api;

import com.jcpineda.filestore.files.domain.FileEntity;
import com.jcpineda.filestore.files.service.FileCreateRequestHasher;
import com.jcpineda.filestore.files.service.FileMetadataService;
import com.jcpineda.filestore.idempotency.service.IdempotencyService;
import com.jcpineda.filestore.security.JwtPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileMetadataController {

    private final FileMetadataService fileMetadataService;
    private final IdempotencyService idempotencyService;
    private final FileCreateRequestHasher fileCreateRequestHasher;

    public FileMetadataController(FileMetadataService fileMetadataService,
                                  IdempotencyService idempotencyService,
                                  FileCreateRequestHasher fileCreateRequestHasher) {
        this.fileMetadataService = fileMetadataService;
        this.idempotencyService = idempotencyService;
        this.fileCreateRequestHasher = fileCreateRequestHasher;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<FileMetadataResponse> create(Authentication authentication,
                                                       @RequestHeader(value = "Idempotency-Key", required = false)
                                                       String idempotencyKey,
                                                       @RequestPart("file") MultipartFile file) {
        UUID ownerId = extractUserId(authentication);
        String requestHash = fileCreateRequestHasher.hash(file);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var replay = idempotencyService.tryReplay(ownerId, idempotencyKey, requestHash, FileMetadataResponse.class);
            if (replay.isPresent()) {
                var stored = replay.get();
                return ResponseEntity.status(stored.responseCode()).body(stored.responseBody());
            }
        }

        FileEntity created = fileMetadataService.create(ownerId, file);
        FileMetadataResponse response = FileMetadataResponse.from(created);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.save(ownerId, idempotencyKey, requestHash, HttpStatus.CREATED.value(), response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<FileListResponse> list(Authentication authentication) {
        UUID ownerId = extractUserId(authentication);
        List<FileMetadataResponse> items = fileMetadataService.list(ownerId)
            .stream()
            .map(FileMetadataResponse::from)
            .toList();
        return ResponseEntity.ok(new FileListResponse(items));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(Authentication authentication,
                                       @PathVariable UUID fileId) {
        UUID ownerId = extractUserId(authentication);
        fileMetadataService.delete(ownerId, fileId);
        return ResponseEntity.noContent().build();
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new IllegalStateException("Authenticated principal is required");
        }
        return principal.userId();
    }
}
