# Consistent Hashing Mechanism

## Purpose

This document explains the consistent-hashing mechanism used in the Scenario 1 storage-sharding lab.

The goal is to route each `ownerId` to a storage shard in a way that is:

1. Deterministic
2. Reasonably balanced
3. Stable when shards are added or removed

## Problem with Simple Modulo Hashing

A naive approach would be:

1. Hash the `ownerId`
2. Compute `hash % shardCount`
3. Use the resulting shard index

This works while the shard count stays fixed, but it has a major weakness:

If the number of shards changes from 3 to 4, almost every key gets reassigned.

That means:

1. Large-scale data movement
2. Cache churn
3. Painful rebalancing
4. Operational instability during topology changes

Consistent hashing reduces that disruption.

## Core Idea

Instead of assigning keys directly by shard count, we place shards on a logical ring.

Then:

1. Hash each shard identifier onto the ring
2. Hash each `ownerId` onto the same ring
3. Walk clockwise until reaching the first shard position
4. Route that owner to that shard

If a shard is added or removed, only the nearby section of the ring is affected.

## How the Lab Implementation Works

### 1. Shards

Each storage node is represented by a simple identifier such as:

1. `minio-a`
2. `minio-b`
3. `minio-c`

That is modeled by [StorageShard.java](/Users/jcpineda/FileStore-SystemDesign/filestore-design-lab/src/main/java/com/jcpineda/filestore/storage/sharding/StorageShard.java#L1).

### 2. Virtual nodes

Real consistent-hash rings often use multiple positions per shard rather than only one.

This implementation uses virtual nodes, meaning each shard is inserted into the ring many times:

1. `minio-a#0`
2. `minio-a#1`
3. `minio-a#2`
4. ...

Why this helps:

1. It smooths out uneven distribution
2. It reduces the chance that one shard owns a very large range
3. It produces more balanced routing for moderate sample sizes

In this lab, the tests use `128` virtual nodes per shard.

That behavior lives in [ConsistentHashRing.java](/Users/jcpineda/FileStore-SystemDesign/filestore-design-lab/src/main/java/com/jcpineda/filestore/storage/sharding/ConsistentHashRing.java#L12).

### 3. Hash function

The ring uses `SHA-256` to hash both:

1. shard virtual-node identifiers
2. owner keys

The implementation takes the first 8 bytes of the digest and converts them into a positive `long`, which becomes the ring position.

This gives:

1. deterministic placement
2. a large hash space
3. stable results across runs

### 4. Routing an owner

Routing works like this:

1. Convert `ownerId` to a string
2. Hash it into a ring position
3. Find the first shard position greater than or equal to that hash
4. If no later position exists, wrap to the first position in the ring

That routing logic is exposed by [OwnerShardRouter.java](/Users/jcpineda/FileStore-SystemDesign/filestore-design-lab/src/main/java/com/jcpineda/filestore/storage/sharding/OwnerShardRouter.java#L6).

## Example

Assume the ring contains virtual-node positions in clockwise order like this:

1. `minio-b`
2. `minio-a`
3. `minio-c`
4. `minio-a`
5. `minio-b`

If `ownerId = user-123` hashes between the second and third positions, that owner routes to `minio-c`.

If `ownerId = user-456` hashes after the final position, routing wraps to the first position and the owner routes to `minio-b`.

## Why Adding a Shard Moves Only Some Owners

When a new shard such as `minio-d` is added:

1. New virtual-node positions are inserted into the ring
2. Only owners that fall into the ranges now claimed by `minio-d` move
3. Owners outside those ranges stay on their current shard

This is the main advantage over modulo-based routing.

Instead of remapping nearly everything, only a subset of owners move.

## Why Removing a Shard Works Cleanly

When a shard such as `minio-b` is removed:

1. Its virtual-node positions disappear from the ring
2. Owners that previously mapped to `minio-b` are reassigned to the next clockwise shard
3. Owners on other shards are unchanged

That makes shard loss or decommissioning much easier to reason about.

## What the Analyzer Measures

The lab also includes a rebalance analyzer in [ShardRebalanceAnalyzer.java](/Users/jcpineda/FileStore-SystemDesign/filestore-design-lab/src/main/java/com/jcpineda/filestore/storage/sharding/ShardRebalanceAnalyzer.java#L9).

It compares two router configurations:

1. before topology change
2. after topology change

For a sample set of owners, it calculates:

1. how many owners moved
2. what fraction of owners moved
3. per-shard counts before the change
4. per-shard counts after the change

This lets us test the important system-design claim directly: topology changes should affect only part of the keyspace.

## What the Tests Prove

The simulation tests in [OwnerShardRouterTest.java](/Users/jcpineda/FileStore-SystemDesign/filestore-design-lab/src/test/java/com/jcpineda/filestore/storage/sharding/OwnerShardRouterTest.java#L10) cover:

1. deterministic routing for the same owner
2. balanced-enough distribution across three shards
3. adding a shard moves only a subset of owners
4. removing a shard moves only owners previously assigned to that shard

## Why `ownerId` Is the Shard Key in This Scenario

We chose `ownerId` because it matches the current application model well:

1. file operations are already owner-scoped
2. ownership is central to auth and list semantics
3. it keeps a user's files colocated on one storage shard in the lab model

This is useful for teaching, but it is not the only possible choice.

If one tenant becomes very hot, `ownerId`-based placement can create hotspots. A future scenario can compare this with `fileId`-based routing.

## Current Limits of the Lab

This mechanism is currently a simulation and design aid, not a production runtime path.

Out of scope for this version:

1. live MinIO cluster membership changes
2. migrating existing objects between shards
3. replica placement
4. failover reads
5. cross-shard repair

## Summary

Consistent hashing gives us a routing strategy where:

1. the same owner consistently maps to the same shard
2. distribution is reasonably balanced with virtual nodes
3. adding or removing a shard moves only part of the keyspace

That makes it a good fit for teaching shard placement and rebalance behavior in this project.
