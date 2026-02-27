# Error Contract

## Standard Error Body

```json
{
  "error": {
    "code": "MAX_SIZE_EXCEEDED",
    "message": "File exceeds 10 MB limit",
    "correlationId": "req_01JXYZABC123"
  }
}
```

## Contract Rules

1. `code` is stable, machine-readable, and intended for client logic.
2. `message` is human-readable and can change without version bump.
3. `correlationId` is always present in responses and logs.
4. Error body shape is identical across REST endpoints.

## Error Codes and HTTP Mapping

| Code | HTTP Status | Meaning |
|---|---:|---|
| `UNAUTHORIZED` | 401 | Missing/invalid JWT or expired token. |
| `FORBIDDEN` | 403 | Authenticated user is not owner of target resource. |
| `FILE_NOT_FOUND` | 404 | File metadata does not exist (or is soft-deleted). |
| `INVALID_FILE_STATE` | 409 | State transition not allowed for the requested action. |
| `MAX_SIZE_EXCEEDED` | 413 | Upload exceeds configured max size limit. |
| `IDEMPOTENCY_CONFLICT` | 409 | Reused idempotency key with different request body. |
| `STORAGE_UNAVAILABLE` | 503 | Object storage operation unavailable or timed out. |
| `INTERNAL_ERROR` | 500 | Unexpected server failure. |

## Examples

### Unauthorized

```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "JWT is missing or invalid",
    "correlationId": "req_01JXYZABC123"
  }
}
```

### Idempotency Conflict

```json
{
  "error": {
    "code": "IDEMPOTENCY_CONFLICT",
    "message": "Idempotency key was already used with a different payload",
    "correlationId": "req_01JXYZABC789"
  }
}
```
