package com.jcpineda.filestore.storage.sharding;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class ShardRebalanceAnalyzer {

    private ShardRebalanceAnalyzer() {
    }

    public static RebalanceReport analyzeOwners(Collection<UUID> ownerIds,
                                                OwnerShardRouter before,
                                                OwnerShardRouter after) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            throw new IllegalArgumentException("ownerIds must not be empty");
        }
        if (before == null || after == null) {
            throw new IllegalArgumentException("Routers must not be null");
        }

        int movedOwners = 0;
        Map<String, Integer> beforeCounts = new TreeMap<>();
        Map<String, Integer> afterCounts = new TreeMap<>();

        for (UUID ownerId : ownerIds) {
            StorageShard beforeShard = before.routeOwner(ownerId);
            StorageShard afterShard = after.routeOwner(ownerId);

            beforeCounts.merge(beforeShard.shardId(), 1, Integer::sum);
            afterCounts.merge(afterShard.shardId(), 1, Integer::sum);

            if (!beforeShard.equals(afterShard)) {
                movedOwners++;
            }
        }

        int sampleSize = ownerIds.size();
        double movedFraction = (double) movedOwners / sampleSize;

        return new RebalanceReport(
            sampleSize,
            movedOwners,
            movedFraction,
            Map.copyOf(new LinkedHashMap<>(beforeCounts)),
            Map.copyOf(new LinkedHashMap<>(afterCounts))
        );
    }

    public record RebalanceReport(int sampleSize,
                                  int movedOwners,
                                  double movedFraction,
                                  Map<String, Integer> beforeCounts,
                                  Map<String, Integer> afterCounts) {
    }
}
