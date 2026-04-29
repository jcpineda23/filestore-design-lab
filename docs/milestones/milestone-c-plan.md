# Milestone C - Exact Plan (Object Storage + SSE)

## Objective
Finish the first real storage and realtime vertical by persisting file bytes in object storage and streaming user-scoped file lifecycle events over SSE.

## Scope Decisions

1. Milestone C is SSE-only.
- WebSocket presence is explicitly deferred beyond this milestone.
- The shipped realtime scope is file lifecycle notifications over `GET /api/v1/events/stream`.

2. MinIO runs as a dedicated container.
- Local and exercise environments use Docker Compose to run MinIO outside the app process.
- This isolates blob storage from application resource contention and makes restart-based recovery easier to validate.

3. Reliability target is restart-and-recover, not high durability.
- Single-node MinIO with a local container volume is sufficient for Milestone C.
- High-availability storage, replication, backups, and cross-zone durability are out of scope.

4. Storage failures must degrade cleanly.
- If MinIO is unavailable, storage-backed operations should fail with `STORAGE_UNAVAILABLE`.
- Once the MinIO container is back, the API should resume normal behavior without code changes.

## Ordered Tickets

1. C1 Scope lock and contract sync (0.5 day)
- Freeze Milestone C as object storage + SSE only.
- Update docs/OpenAPI to match shipped scope.
- Document MinIO as a dedicated container dependency with non-HA assumptions.

2. C2 Storage vertical hardening (1 day)
- Verify upload/download/delete behavior against the current lifecycle rules.
- Tighten failure handling for unavailable object storage.

3. C3 SSE behavior hardening (1 day)
- Validate emitted event types and ordering for upload, download, and delete flows.
- Clarify reconnect behavior for v1 without introducing WebSocket scope.

4. C4 Vertical integration coverage (1 day)
- Add or strengthen automated coverage for the storage happy path and storage failure path.
- Ensure tests prove metadata and blob consistency expectations.

5. C5 Live MinIO runbook execution (0.5 day)
- Exercise the Docker Compose MinIO path end to end.
- Capture the exact operator runbook for upload, delete, and recovery drills.

6. C6 Docs and repo ergonomics (0.5 day)
- Refresh README instructions and milestone notes.
- Keep lifecycle, contracts, and runbooks aligned with actual behavior.

## Definition of Done

1. File upload stores bytes in MinIO and returns metadata in `READY`.
2. File download returns the original stored bytes.
3. File delete removes the object and hides deleted metadata from default listing.
4. When MinIO is stopped, storage-backed operations return `503 STORAGE_UNAVAILABLE`.
5. After MinIO restarts, storage-backed operations work again without app code changes.
6. SSE is the only in-scope realtime transport for Milestone C and its contract/docs match implementation.

## Non-Goals

1. WebSocket presence.
2. Broker-backed event fan-out.
3. Durable object storage replication or backup strategy.
4. Multi-region or disaster recovery behavior.
