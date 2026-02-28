CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE files (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    file_name VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    checksum VARCHAR(255),
    status VARCHAR(32) NOT NULL CHECK (
        status IN ('UPLOADING', 'READY', 'FAILED', 'DELETING', 'DELETE_FAILED')
    ),
    failure_reason VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_code INT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (owner_id, key)
);

CREATE INDEX idx_files_owner_created_at ON files(owner_id, created_at DESC);
CREATE INDEX idx_files_owner_status ON files(owner_id, status);
CREATE INDEX idx_files_deleted_at ON files(deleted_at);
CREATE INDEX idx_idempotency_owner_created_at ON idempotency_keys(owner_id, created_at DESC);
