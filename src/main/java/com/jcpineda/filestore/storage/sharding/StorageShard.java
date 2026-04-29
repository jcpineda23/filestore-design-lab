package com.jcpineda.filestore.storage.sharding;

public record StorageShard(String shardId) {

    public StorageShard {
        if (shardId == null || shardId.isBlank()) {
            throw new IllegalArgumentException("shardId must not be blank");
        }
    }
}
