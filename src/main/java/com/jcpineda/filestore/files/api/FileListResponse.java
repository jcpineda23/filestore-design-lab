package com.jcpineda.filestore.files.api;

import java.util.List;

public record FileListResponse(List<FileMetadataResponse> items) {
}
