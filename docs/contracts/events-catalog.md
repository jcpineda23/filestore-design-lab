# Events Catalog

## Transport Events

### `connected`

This is the initial SSE transport event sent immediately after `GET /api/v1/events/stream` is established.

```text
event: connected
data: ok
```

It is a connection bootstrap signal, not a domain event envelope.

## File Events

### `file.upload.started`

```json
{
  "data": {
    "fileName": "notes.pdf",
    "sizeBytes": 1048576,
    "contentType": "application/pdf"
  }
}
```

### `file.upload.progress`

```json
{
  "data": {
    "bytesReceived": 524288,
    "sizeBytes": 1048576,
    "percent": 50
  }
}
```

### `file.upload.completed`

```json
{
  "data": {
    "fileName": "notes.pdf",
    "sizeBytes": 1048576,
    "checksum": "sha256:abcd1234"
  }
}
```

### `file.upload.failed`

```json
{
  "data": {
    "reason": "MAX_SIZE_EXCEEDED",
    "message": "File exceeds 10 MB limit"
  }
}
```

### `file.downloaded`

```json
{
  "data": {
    "fileName": "notes.pdf"
  }
}
```

### `file.deleted`

```json
{
  "data": {
    "fileName": "notes.pdf"
  }
}
```

### `file.delete.failed`

```json
{
  "data": {
    "reason": "STORAGE_UNAVAILABLE",
    "message": "Object deletion failed"
  }
}
```

## Delivery Semantics (v1)

1. Live delivery is at-most-once.
2. Ordering is guaranteed per user stream, not globally.
3. For a successful upload on an already-open user stream, the expected order is `file.upload.started -> file.upload.progress -> file.upload.completed`.
4. For an upload that fails because storage is unavailable, the expected order is `file.upload.started -> file.upload.failed`.
5. A successful delete emits `file.deleted` after blob deletion and metadata soft delete both complete.
6. A failed delete emits `file.delete.failed` and leaves the file visible for a later retry.
7. Reconnects are treated as fresh live subscriptions. Missed events are not replayed in v1.
8. `Last-Event-ID` is currently ignored in v1.
9. WebSocket presence events are deferred beyond Milestone C.
