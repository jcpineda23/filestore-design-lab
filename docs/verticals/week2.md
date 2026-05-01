# Week 2 Vertical System-Design Test Matrix

This runbook focuses on the first real storage and eventing vertical.

## Prerequisites

1. Start infrastructure:
```bash
docker compose up -d postgres minio
```

2. Start API:
```bash
DB_URL=jdbc:postgresql://localhost:5433/filestore \
DB_USERNAME=filestore \
DB_PASSWORD=filestore \
MINIO_ENDPOINT=http://localhost:9000 \
MINIO_ACCESS_KEY=minioadmin \
MINIO_SECRET_KEY=minioadmin \
mvn spring-boot:run
```

3. Verify C1 state:
```bash
./scripts/verify_c1_state.sh
```

4. Run the core Week 2 vertical script:
```bash
./scripts/run_week2_verticals.sh
```

5. Run the outage drill when you are ready:
```bash
RUN_FAILURE_DRILL=true ./scripts/run_week2_verticals.sh
```

6. Run the full restart-and-recover MinIO drill:
```bash
./scripts/run_minio_recovery_drill.sh
```

## Vertical 1: Real Upload Storage Path

Goal: validate the upload path stores bytes in object storage before finalizing metadata.

1. Open an SSE stream with a valid JWT.
2. Upload a file with `POST /api/v1/files`.
3. Download the same file with `GET /api/v1/files/{fileId}/download`.
4. Confirm bytes match original upload.

Expected:
1. Upload returns `201`.
2. Download returns `200` with original content bytes.
3. File row status ends in `READY`.
4. SSE stream receives `file.upload.started -> file.upload.progress -> file.upload.completed` in that order.

Automation:
```bash
./scripts/run_week2_verticals.sh
```

## Vertical 2: Delete Consistency

Goal: validate metadata and blob deletion stay coordinated.

1. Upload a file.
2. Delete it with `DELETE /api/v1/files/{fileId}`.
3. Verify it disappears from `GET /api/v1/files`.
4. Attempt download again.

Expected:
1. Delete returns `204`.
2. List excludes the deleted file.
3. Download returns `404` after delete.
4. SSE emits `file.deleted` after the upload lifecycle completes in this scenario.

Automation:
```bash
./scripts/run_week2_verticals.sh
```

## Vertical 3: Storage Failure Drill

Goal: observe consistency behavior when blob storage is unavailable.

1. Stop MinIO.
2. Attempt upload.
3. Restart MinIO.
4. Inspect API error and DB state.

Expected:
1. Upload returns `503 STORAGE_UNAVAILABLE`.
2. File row ends in `FAILED`.
3. SSE emits `file.upload.failed`.
4. No object remains partially accessible.

Automation:
```bash
RUN_FAILURE_DRILL=true ./scripts/run_week2_verticals.sh
```

## Vertical 4: MinIO Restart-And-Recover Drill

Goal: validate the Milestone C operational promise that storage-backed APIs recover once MinIO returns, without restarting the Spring Boot app.

1. Upload one file that will be used for outage download checks.
2. Upload a second file that will be used for outage delete checks.
3. Stop MinIO.
4. Confirm upload, download, and delete all return `503 STORAGE_UNAVAILABLE`.
5. Confirm SSE emits failure events during the outage.
6. Restart MinIO.
7. Confirm upload, download, and delete all work again without restarting the API process.

Expected:
1. `/actuator/health` reports storage degradation while MinIO is down.
2. Upload, download, and delete each fail cleanly during the outage.
3. After MinIO restarts, a new upload succeeds, the pre-outage download succeeds with original bytes, and the previously failed delete can be retried successfully.
4. `file.deleted` is emitted when the delete retry finally succeeds.

Automation:
```bash
./scripts/run_minio_recovery_drill.sh
```
