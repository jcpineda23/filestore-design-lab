# ERD (Milestone A)

```mermaid
erDiagram
    USERS ||--o{ FILES : owns
    USERS ||--o{ IDEMPOTENCY_KEYS : submits

    USERS {
      uuid id PK
      varchar email UK
      varchar password_hash
      timestamptz created_at
      timestamptz updated_at
    }

    FILES {
      uuid id PK
      uuid owner_id FK
      varchar file_name
      varchar content_type
      bigint size_bytes
      varchar object_key UK
      varchar checksum
      varchar status
      varchar failure_reason
      timestamptz created_at
      timestamptz updated_at
      timestamptz deleted_at
    }

    IDEMPOTENCY_KEYS {
      uuid id PK
      uuid owner_id FK
      varchar idempotency_key
      varchar request_hash
      int response_code
      text response_body
      timestamptz created_at
    }
```

## Notes

1. `files.owner_id` scopes every file to a single user.
2. `files.object_key` is unique and maps metadata to object storage.
3. `idempotency_keys` is scoped by `(owner_id, idempotency_key)`.
4. `deleted_at` enables soft deletes and safe recovery/audit.
