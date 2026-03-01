# Week 1 Vertical System-Design Test Matrix

This runbook focuses on end-to-end vertical checks over isolated component checks.

## Prerequisites

1. Start database:
```bash
docker compose up -d postgres
```

2. Start API:
```bash
DB_URL=jdbc:postgresql://localhost:5433/filestore \
DB_USERNAME=filestore \
DB_PASSWORD=filestore \
mvn spring-boot:run
```

## Vertical 1: Auth + Metadata Happy Path

Goal: validate baseline product flow and ownership scope.

1. Register and login.
2. Create metadata entry with `POST /api/v1/files`.
3. List files and verify one entry.
4. Delete file and verify list is empty.

Expected:
1. `201` on register and file create.
2. `200` on login and list.
3. `204` on delete.
4. Only current user sees/deletes own files.

## Vertical 2: Failure and Contract Behavior

Goal: validate deterministic errors and state guards.

1. Access `/api/v1/files` without token.
2. Delete non-existent file.
3. Send empty file upload.

Expected:
1. `401 UNAUTHORIZED` without token.
2. `404 FILE_NOT_FOUND` for unknown file id.
3. `400 VALIDATION_ERROR` for invalid upload.
4. Error body always includes `error.code`, `error.message`, `error.correlationId`.

## Vertical 3: Idempotency and Retry Safety

Goal: validate duplicate suppression and conflict handling.

1. Send `POST /api/v1/files` with `Idempotency-Key: K1` and file payload A.
2. Repeat same request with same key and same payload A.
3. Send same key `K1` with different payload B.

Expected:
1. First call `201` and creates one file row.
2. Second call `201` replay with same file id, no extra row.
3. Third call `409 IDEMPOTENCY_CONFLICT`.

## Quick Runner

Use:
```bash
./scripts/run_week1_verticals.sh
```

The script prints each step and exits on first failure.
