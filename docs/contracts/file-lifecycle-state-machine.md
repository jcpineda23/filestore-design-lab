# File Lifecycle State Machine

## States

1. `UPLOADING`
2. `READY`
3. `FAILED`
4. `DELETING`
5. `DELETE_FAILED`
6. `DELETED` (logical terminal state represented by `deleted_at`)

## Valid Transitions

1. `UPLOADING -> READY`
- Upload stream and object finalization succeeded.

2. `UPLOADING -> FAILED`
- Upload rejected or storage write failed.

3. `READY -> DELETING`
- Authenticated owner requested delete.

4. `DELETING -> DELETED`
- Object deleted and metadata soft-deleted (`deleted_at` set).

5. `DELETING -> DELETE_FAILED`
- Object deletion failed in storage layer.

6. `DELETE_FAILED -> DELETING`
- Retry delete operation.

## Invalid Transitions

1. `READY -> READY`
2. `FAILED -> READY`
3. `DELETED -> *`
4. `UPLOADING -> DELETING` (only allowed after successful upload in v1)

## Action Guardrails

1. Download allowed only in `READY`.
2. Delete allowed only in `READY` or `DELETE_FAILED`.
3. Listing excludes `DELETED` by default (`deleted_at IS NULL`).
