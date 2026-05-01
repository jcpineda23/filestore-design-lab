# MinIO Recovery Drill

This runbook exercises the most important Milestone C operational claim:

If MinIO goes down, storage-backed APIs should fail cleanly with `503 STORAGE_UNAVAILABLE`.
When MinIO comes back, the application should resume normal upload, download, and delete behavior without restarting the Spring Boot process.

## Why This Drill Matters

At a Staff or Principal level, this is the point of the vertical:

1. We are proving behavior under partial dependency failure, not just happy-path CRUD.
2. We are validating the system boundary between metadata in PostgreSQL and bytes in object storage.
3. We are checking that failure is externally visible through API responses, health signals, and SSE.
4. We are verifying restart-and-recover semantics, which are the intended reliability target for Milestone C.

## Prerequisites

1. Start PostgreSQL and MinIO:
```bash
docker compose up -d postgres minio
```

2. Start the app:
```bash
DB_URL=jdbc:postgresql://localhost:5433/filestore \
DB_USERNAME=filestore \
DB_PASSWORD=filestore \
MINIO_ENDPOINT=http://localhost:9000 \
MINIO_ACCESS_KEY=minioadmin \
MINIO_SECRET_KEY=minioadmin \
mvn spring-boot:run
```

3. Verify the steady state:
```bash
./scripts/verify_c1_state.sh
```

## Automated Drill

Run:

```bash
./scripts/run_minio_recovery_drill.sh
```

The script writes a timestamped markdown log under `docs/verticals/logs/`.

## What The Drill Proves

The automation performs this exact sequence:

1. Registers a user, logs in, and opens an SSE stream.
2. Uploads one file for download validation after recovery.
3. Uploads a second file for delete-retry validation after recovery.
4. Stops the MinIO container.
5. Confirms `/actuator/health` reflects storage degradation.
6. Confirms upload, download, and delete each return `503 STORAGE_UNAVAILABLE`.
7. Confirms SSE emits `file.upload.failed` and `file.delete.failed`.
8. Restarts MinIO.
9. Re-runs readiness verification without restarting the Spring Boot app.
10. Confirms:
   - a new upload succeeds
   - the pre-outage download succeeds with matching bytes
   - the previously failed delete succeeds on retry
11. Confirms `file.deleted` is emitted when delete finally completes.

## Expected Outcomes

If the drill passes, the system is demonstrating the intended Milestone C resilience model:

1. Failure is clean and explicit.
2. Metadata and object storage remain understandable under outage.
3. Recovery is operator-driven and simple: restart MinIO, not the app.
4. The realtime channel surfaces both failure and eventual success.

## If The Drill Fails

Use the generated log to narrow the failure domain:

1. If readiness fails before the outage, treat it as environment setup.
2. If health never goes `DOWN`, inspect the storage health indicator wiring.
3. If APIs do not return `503`, inspect storage exception translation and file-state transitions.
4. If recovery fails after MinIO restarts, inspect MinIO client configuration, bucket initialization, and whether the app still sees storage as unhealthy.
