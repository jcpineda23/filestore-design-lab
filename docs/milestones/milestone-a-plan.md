# Milestone A - Exact Plan (Design Blueprint)

## Objective
Lock the full system design and contracts before implementation.

## Deliverables

1. Requirements document
- Functional scope (auth, upload, list, download, delete, SSE, WebSocket presence).
- Non-goals (sharing, versioning, resumable upload, antivirus, multi-region).

2. API contract
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/files`
- `GET /api/v1/files`
- `GET /api/v1/files/{fileId}/download`
- `DELETE /api/v1/files/{fileId}`
- `GET /api/v1/events/stream` (SSE)
- `GET /api/v1/ws` (WebSocket upgrade)

3. Event contract
- Shared event envelope: `eventId`, `eventType`, `occurredAt`, `userId`, `resourceType`, `resourceId`, `correlationId`, `data`.
- Event types:
  - `file.upload.started`
  - `file.upload.progress`
  - `file.upload.completed`
  - `file.upload.failed`
  - `file.deleted`
  - `file.delete.failed`
  - `file.downloaded`
  - `presence.user.online`
  - `presence.user.offline`
  - `presence.heartbeat.missed`

4. Data model (PostgreSQL)
- `users`
- `files`
- `idempotency_keys`
- Indexing and uniqueness constraints.

5. Flow diagrams
- Upload sequence (REST + SSE + DB + storage).
- Download sequence.
- Delete sequence.

6. File lifecycle state machine
- `UPLOADING -> READY`
- `UPLOADING -> FAILED`
- `READY -> DELETING -> (soft/hard deleted)`
- `DELETING -> DELETE_FAILED`

7. Error contract
- Standard JSON shape with `code`, `message`, `correlationId`.
- Core codes:
  - `UNAUTHORIZED`
  - `FORBIDDEN`
  - `FILE_NOT_FOUND`
  - `INVALID_FILE_STATE`
  - `MAX_SIZE_EXCEEDED`
  - `IDEMPOTENCY_CONFLICT`
  - `STORAGE_UNAVAILABLE`
  - `INTERNAL_ERROR`

## Acceptance Criteria

1. OpenAPI draft is defined for all v1 endpoints.
2. Event catalog and payload examples are documented.
3. ERD and SQL migration draft are documented.
4. Sequence diagrams for upload/download/delete are documented.
5. File lifecycle transitions are documented and validated.
6. SSE vs WebSocket responsibilities are explicitly defined.
7. Milestone A review approved before writing implementation code.

## Timebox (recommended)

1. Day 1: Requirements + endpoint contract.
2. Day 2: Event contract + transport mapping.
3. Day 3: ERD + migration draft + state machine.
4. Day 4: Sequence diagrams + acceptance review.
