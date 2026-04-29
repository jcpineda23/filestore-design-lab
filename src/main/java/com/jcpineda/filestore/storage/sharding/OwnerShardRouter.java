package com.jcpineda.filestore.storage.sharding;

import java.util.Collection;
import java.util.UUID;

public class OwnerShardRouter {

    private final ConsistentHashRing hashRing;

    public OwnerShardRouter(Collection<StorageShard> shards, int virtualNodesPerShard) {
        this.hashRing = new ConsistentHashRing(shards, virtualNodesPerShard);
    }

    public StorageShard routeOwner(UUID ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        return hashRing.route(ownerId.toString());
    }

    public int virtualNodesPerShard() {
        return hashRing.virtualNodesPerShard();
    }
}
