package com.jcpineda.filestore.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("test")
public class InMemoryObjectStorageService implements ObjectStorageService {

    private final Map<String, InMemoryStoredObject> objects = new ConcurrentHashMap<>();

    @Override
    public StorageObjectMetadata putObject(String objectKey, MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String checksum = Integer.toHexString(java.util.Arrays.hashCode(bytes));
        objects.put(objectKey, new InMemoryStoredObject(bytes, contentType));
        return new StorageObjectMetadata(objectKey, checksum, bytes.length, contentType);
    }

    @Override
    public StoredObject getObject(String objectKey) throws IOException {
        InMemoryStoredObject object = objects.get(objectKey);
        if (object == null) {
            throw new StorageOperationException("Object not found in in-memory storage", null);
        }
        return new StoredObject(new ByteArrayInputStream(object.bytes()), object.contentType(), object.bytes().length);
    }

    @Override
    public void deleteObject(String objectKey) throws IOException {
        objects.remove(objectKey);
    }

    private record InMemoryStoredObject(byte[] bytes, String contentType) {
    }
}
