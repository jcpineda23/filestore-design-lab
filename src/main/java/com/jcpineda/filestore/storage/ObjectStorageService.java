package com.jcpineda.filestore.storage;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface ObjectStorageService {

    StorageObjectMetadata putObject(String objectKey, MultipartFile file) throws IOException;

    StoredObject getObject(String objectKey) throws IOException;

    void deleteObject(String objectKey) throws IOException;
}
