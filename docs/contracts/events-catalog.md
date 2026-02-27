# Events Catalog

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

## Presence Events (WebSocket-focused in v1)

### `presence.user.online`

```json
{
  "data": {
    "sessionId": "ses_01JXYZSESSION1"
  }
}
```

### `presence.user.offline`

```json
{
  "data": {
    "sessionId": "ses_01JXYZSESSION1",
    "reason": "disconnect"
  }
}
```

### `presence.heartbeat.missed`

```json
{
  "data": {
    "sessionId": "ses_01JXYZSESSION1",
    "missedCount": 3
  }
}
```

## Delivery Semantics (v1)

1. Live delivery is at-most-once.
2. Ordering is guaranteed per user stream, not globally.
3. Optional short replay window for SSE via `Last-Event-ID`.
