# Scenario 1 - OwnerId Sharding with Consistent Hashing

## Goal
Exercise object-storage node placement by routing each owner's files to a storage shard selected from a consistent hash ring.

## Why this scenario

1. It fits the current project shape because all file operations are already owner-scoped.
2. It demonstrates the core benefit of consistent hashing: adding or removing a node moves only part of the keyspace.
3. It keeps the exercise focused on placement and rebalance behavior, without introducing replication or broker complexity.

## Model

1. Shard key: `ownerId`
2. Routed resource: object storage shard
3. Initial topology:
- `minio-a`
- `minio-b`
- `minio-c`
4. Hashing strategy:
- consistent hash ring
- 128 virtual nodes per shard in the lab implementation

## Exercise Steps

1. Route a sample population of owners across `minio-a`, `minio-b`, and `minio-c`.
2. Add `minio-d` and measure the percentage of owners that move.
3. Remove `minio-b` and confirm only owners mapped to `minio-b` are remapped.
4. Compare the before/after distribution to see how balanced the ring is.

## Expected Behaviors

1. Owner placement is deterministic.
2. Adding one shard should move only a minority of owners, not the full dataset.
3. Removing one shard should remap only owners that previously landed on that shard.
4. Distribution should stay reasonably balanced across nodes when enough owners are sampled.

## Code Pointers

1. Router: [OwnerShardRouter.java](/Users/jcpineda/FileStore-SystemDesign/filestore-design-lab/src/main/java/com/jcpineda/filestore/storage/sharding/OwnerShardRouter.java)
2. Hash ring: [ConsistentHashRing.java](/Users/jcpineda/FileStore-SystemDesign/filestore-design-lab/src/main/java/com/jcpineda/filestore/storage/sharding/ConsistentHashRing.java)
3. Rebalance analysis: [ShardRebalanceAnalyzer.java](/Users/jcpineda/FileStore-SystemDesign/filestore-design-lab/src/main/java/com/jcpineda/filestore/storage/sharding/ShardRebalanceAnalyzer.java)
4. Simulation tests: [OwnerShardRouterTest.java](/Users/jcpineda/FileStore-SystemDesign/filestore-design-lab/src/test/java/com/jcpineda/filestore/storage/sharding/OwnerShardRouterTest.java)

## Out of Scope

1. Runtime MinIO cluster membership changes.
2. Replica placement.
3. Cross-shard reads or repair.
4. Rebalancing live objects between shards.
