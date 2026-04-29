package com.jcpineda.filestore.storage.sharding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OwnerShardRouterTest {

    private static final int VIRTUAL_NODES = 128;

    @Test
    void routeOwnerIsDeterministicForTheSameOwnerId() {
        OwnerShardRouter router = new OwnerShardRouter(List.of(
            new StorageShard("minio-a"),
            new StorageShard("minio-b"),
            new StorageShard("minio-c")
        ), VIRTUAL_NODES);

        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000123");

        StorageShard first = router.routeOwner(ownerId);
        StorageShard second = router.routeOwner(ownerId);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void removingAShardOnlyMovesOwnersPreviouslyAssignedToThatShard() {
        OwnerShardRouter before = new OwnerShardRouter(List.of(
            new StorageShard("minio-a"),
            new StorageShard("minio-b"),
            new StorageShard("minio-c")
        ), VIRTUAL_NODES);

        OwnerShardRouter after = new OwnerShardRouter(List.of(
            new StorageShard("minio-a"),
            new StorageShard("minio-c")
        ), VIRTUAL_NODES);

        List<UUID> ownerIds = sampleOwnerIds(10_000);
        long ownersOnRemovedShard = ownerIds.stream()
            .filter(ownerId -> before.routeOwner(ownerId).equals(new StorageShard("minio-b")))
            .count();

        var report = ShardRebalanceAnalyzer.analyzeOwners(ownerIds, before, after);

        assertThat(report.movedOwners()).isEqualTo((int) ownersOnRemovedShard);
        assertThat(report.afterCounts()).doesNotContainKey("minio-b");
    }

    @Test
    void addingAShardMovesOnlyASubsetOfOwners() {
        OwnerShardRouter before = new OwnerShardRouter(List.of(
            new StorageShard("minio-a"),
            new StorageShard("minio-b"),
            new StorageShard("minio-c")
        ), VIRTUAL_NODES);

        OwnerShardRouter after = new OwnerShardRouter(List.of(
            new StorageShard("minio-a"),
            new StorageShard("minio-b"),
            new StorageShard("minio-c"),
            new StorageShard("minio-d")
        ), VIRTUAL_NODES);

        var report = ShardRebalanceAnalyzer.analyzeOwners(sampleOwnerIds(10_000), before, after);

        assertThat(report.movedOwners()).isGreaterThan(0);
        assertThat(report.movedFraction()).isLessThan(0.40);
        assertThat(report.afterCounts()).containsKey("minio-d");
    }

    @Test
    void distributionStaysReasonablyBalancedAcrossThreeShards() {
        OwnerShardRouter router = new OwnerShardRouter(List.of(
            new StorageShard("minio-a"),
            new StorageShard("minio-b"),
            new StorageShard("minio-c")
        ), VIRTUAL_NODES);

        var report = ShardRebalanceAnalyzer.analyzeOwners(
            sampleOwnerIds(12_000),
            router,
            router
        );

        assertThat(report.beforeCounts().values()).allMatch(count -> count > 3_000 && count < 5_000);
        assertThat(report.movedOwners()).isZero();
    }

    private List<UUID> sampleOwnerIds(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> UUID.nameUUIDFromBytes(("owner-" + i).getBytes()))
            .toList();
    }
}
