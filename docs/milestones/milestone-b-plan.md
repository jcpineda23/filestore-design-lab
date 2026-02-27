# Milestone B - Exact Plan (Auth + Metadata)

## Objective
Implement authentication and file metadata CRUD (without full file-byte transfer pipeline).

## Ordered Tickets

1. B1 Project bootstrap (0.5 day)
- Spring Boot skeleton, profiles, health endpoint, Docker Compose for Postgres.

2. B2 Database migrations (0.5 day)
- Create `users`, `files`, `idempotency_keys` + indexes and constraints.

3. B3 Auth domain + persistence (1 day)
- User repository, password hash strategy, registration/login service.

4. B4 JWT security integration (1 day)
- JWT issue/verify, auth filter chain, protected route policy.

5. B5 Auth API endpoints (0.5 day)
- Register/login controllers + validation + error mapping.

6. B6 File metadata domain (0.75 day)
- File entity/repository, status model, ownership checks.

7. B7 Metadata APIs (1 day)
- `POST /api/v1/files` (metadata stub), `GET /api/v1/files`, `DELETE /api/v1/files/{fileId}`.

8. B8 Idempotency handling (0.75 day)
- `Idempotency-Key` processing and replay for create path.

9. B9 Error contract integration (0.5 day)
- Global exception handling to standard error payload.

10. B10 Tests (1.5 days)
- Unit tests for services + integration tests for auth, ownership, idempotency.

11. B11 Docs and OpenAPI sync (0.5 day)
- Keep docs/spec aligned with implementation.

## Critical Dependencies

1. `B2` before domain/repository work.
2. `B3 + B4` before protected file endpoints.
3. `B7` before `B8`.
4. `B10` gates milestone completion.

## Definition of Done

1. Users can register/login and get JWT.
2. Protected metadata endpoints enforce ownership.
3. Create metadata endpoint supports idempotent replay.
4. Standard error contract is active with `correlationId`.
5. Tests pass for happy path and failure cases.
