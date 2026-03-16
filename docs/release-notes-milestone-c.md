# Release Notes - Milestone C

## Summary

Milestone C adds the first real storage and eventing vertical to the project.
File uploads now persist bytes outside PostgreSQL, downloads return stored content,
and the API can stream user-scoped lifecycle events over SSE.

## Included Changes

### Object storage

- Added a storage abstraction for blob operations.
- Added local MinIO support for development.
- Added an in-memory storage implementation for the test profile.
- Updated the upload flow to store file bytes before finalizing metadata.
- Added object-backed download support.

### File lifecycle behavior

- Uploads now transition through `UPLOADING` to `READY` or `FAILED`.
- Deletes now attempt object removal before soft-deleting metadata.
- Storage failures now surface as `STORAGE_UNAVAILABLE`.

### Real-time events

- Added `GET /api/v1/events/stream` for SSE connections.
- Added user-scoped event publishing for:
  - `file.upload.started`
  - `file.upload.progress`
  - `file.upload.completed`
  - `file.upload.failed`
  - `file.downloaded`
  - `file.deleted`
  - `file.delete.failed`

### Local development

- Added MinIO to `docker-compose`.
- Updated local defaults to use PostgreSQL on port `5433`.
- Updated README run instructions for MinIO-enabled local runs.
- Added a Week 2 vertical test runbook focused on storage and SSE behavior.

### Test coverage

- Added integration coverage for downloading stored bytes.
- Added SSE authentication/smoke coverage.
- Full test suite passes after the Milestone C changes.

## Current limitations

- Live MinIO integration has not yet been exercised through a full scripted vertical in this commit.
- SSE is implemented and smoke-tested, but richer client-side event consumption checks still need to be added.
