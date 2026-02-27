# ADR-002: Metadata vs Blob Storage Separation

## Status
Accepted (Milestone A)

## Context
File systems require different access patterns for metadata and binary content.
Combining both in one persistence layer complicates scaling and reliability.

## Decision
1. Store file metadata in PostgreSQL.
2. Store file bytes in S3-compatible object storage (MinIO in local dev).
3. Link both through `files.object_key`.

## Rationale
1. Relational database fits ownership, indexing, and query/filter requirements.
2. Object storage fits large binary data durability and streaming patterns.
3. Separation is a core system design concept and eases horizontal API scaling.

## Consequences
1. File operations must coordinate metadata and blob operations.
2. Failure handling must support partial-failure states (`FAILED`, `DELETE_FAILED`).
3. Observability must include correlation between DB rows and object operations.

## Deferred Decisions
1. Direct-to-object-store upload via presigned URL vs API-proxied upload.
2. Lifecycle policies, archival tiers, and cross-region replication.
