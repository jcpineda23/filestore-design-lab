package com.jcpineda.filestore.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("!test")
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient minioClient;
    private final StorageProperties properties;

    public MinioObjectStorageService(MinioClient minioClient, StorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @PostConstruct
    void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            }
        } catch (Exception ex) {
            throw new StorageOperationException("Could not initialize MinIO bucket", ex);
        }
    }

    @Override
    public StorageObjectMetadata putObject(String objectKey, MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(resolveContentType(file))
                    .build());

            var stat = minioClient.statObject(
                StatObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
            return new StorageObjectMetadata(objectKey, stat.etag(), stat.size(), resolveContentType(file));
        } catch (Exception ex) {
            throw new StorageOperationException("Could not store object in MinIO", ex);
        }
    }

    @Override
    public StoredObject getObject(String objectKey) throws IOException {
        try {
            var stat = minioClient.statObject(
                StatObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
            InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
            return new StoredObject(stream, stat.contentType(), stat.size());
        } catch (Exception ex) {
            throw new StorageOperationException("Could not read object from MinIO", ex);
        }
    }

    @Override
    public void deleteObject(String objectKey) throws IOException {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
        } catch (Exception ex) {
            throw new StorageOperationException("Could not delete object from MinIO", ex);
        }
    }

    private String resolveContentType(MultipartFile file) {
        return file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    }
}
