package com.jcpineda.filestore.storage.sharding;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

public class ConsistentHashRing {

    private static final String HASH_ALGORITHM = "SHA-256";

    private final NavigableMap<Long, StorageShard> ring;
    private final int virtualNodesPerShard;

    public ConsistentHashRing(Collection<StorageShard> shards, int virtualNodesPerShard) {
        if (shards == null || shards.isEmpty()) {
            throw new IllegalArgumentException("At least one shard is required");
        }
        if (virtualNodesPerShard <= 0) {
            throw new IllegalArgumentException("virtualNodesPerShard must be positive");
        }

        this.virtualNodesPerShard = virtualNodesPerShard;
        this.ring = new TreeMap<>();
        addShards(shards);
    }

    public StorageShard route(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }

        long hash = hashToLong(key);
        var entry = ring.ceilingEntry(hash);
        return entry != null ? entry.getValue() : ring.firstEntry().getValue();
    }

    public int virtualNodesPerShard() {
        return virtualNodesPerShard;
    }

    private void addShards(Collection<StorageShard> shards) {
        for (StorageShard shard : shards) {
            Objects.requireNonNull(shard, "shard must not be null");
            for (int i = 0; i < virtualNodesPerShard; i++) {
                ring.put(hashToLong(shard.shardId() + "#" + i), shard);
            }
        }
    }

    private long hashToLong(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(bytes, 0, Long.BYTES).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
