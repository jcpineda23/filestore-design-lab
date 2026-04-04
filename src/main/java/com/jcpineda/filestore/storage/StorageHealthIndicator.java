package com.jcpineda.filestore.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.util.Optional;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {

    private final ObjectStorageService objectStorageService;
    private final Optional<MinioClient> minioClient;
    private final StorageProperties properties;

    public StorageHealthIndicator(
        ObjectStorageService objectStorageService,
        Optional<MinioClient> minioClient,
        StorageProperties properties
    ) {
        this.objectStorageService = objectStorageService;
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (objectStorageService instanceof InMemoryObjectStorageService) {
            return Health.up()
                .withDetail("provider", "in-memory")
                .withDetail("bucket", properties.bucket())
                .build();
        }

        if (minioClient.isEmpty()) {
            return Health.unknown()
                .withDetail("provider", "minio")
                .withDetail("reason", "MinIO client bean not configured")
                .build();
        }

        try {
            boolean bucketExists = minioClient.get().bucketExists(
                BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!bucketExists) {
                return Health.outOfService()
                    .withDetail("provider", "minio")
                    .withDetail("endpoint", properties.endpoint())
                    .withDetail("bucket", properties.bucket())
                    .withDetail("bucketExists", false)
                    .build();
            }

            return Health.up()
                .withDetail("provider", "minio")
                .withDetail("endpoint", properties.endpoint())
                .withDetail("bucket", properties.bucket())
                .withDetail("bucketExists", true)
                .build();
        } catch (Exception ex) {
            return Health.down(ex)
                .withDetail("provider", "minio")
                .withDetail("endpoint", properties.endpoint())
                .withDetail("bucket", properties.bucket())
                .build();
        }
    }
}
