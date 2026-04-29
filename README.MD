# FileStore Design Lab (Java)

A learning-focused Java project to practice core system design concepts by building a file storage application with authentication, REST APIs, SSE, and future WebSocket support.

## Current Phase
Milestone C in progress.

- Milestone A: system design blueprint (requirements, APIs, event contracts, DB schema, diagrams).
- Milestone B: auth + metadata implementation.
  - B1 complete: Spring Boot bootstrap + profiles + health endpoint + Docker Compose.
  - B2 complete: Flyway migration for `users`, `files`, `idempotency_keys`.
- Milestone C: object storage + SSE.
  - Scope is SSE-only for this milestone; WebSocket presence is deferred.
  - Local MinIO object storage support added.
  - File bytes are now persisted outside PostgreSQL.
  - SSE endpoint added for user-scoped lifecycle events.

Implemented so far: auth, file metadata CRUD, idempotent create, local object storage, download, and SSE stream bootstrap.

## Repository Structure

- `docs/milestones/milestone-a-plan.md`: exact Milestone A plan.
- `docs/milestones/milestone-b-plan.md`: exact Milestone B plan.
- `docs/milestones/milestone-c-plan.md`: exact Milestone C plan.
- `docs/architecture/milestone-a-implementation-structure.md`: implementation structure for Milestone A artifacts.
- `docs/contracts/`: API/event/schema contract documents.
- `docs/diagrams/`: sequence diagrams and architecture visuals.
- `docs/diagrams/overall-architecture.md`: interview-style high-level system design diagram.
- `src/main/java/`: reserved for future implementation.
- `src/test/java/`: reserved for future tests.

## Tech Baseline (planned)

- Java 21
- Spring Boot
- PostgreSQL
- MinIO (S3-compatible)
- Docker Compose

## Run Locally

1. Start PostgreSQL and MinIO in containers:
```bash
docker compose up -d postgres minio
```

2. Run app:
```bash
DB_URL=jdbc:postgresql://localhost:5433/filestore ./mvnw spring-boot:run
```

If Maven Wrapper is not present yet, use:
```bash
DB_URL=jdbc:postgresql://localhost:5433/filestore mvn spring-boot:run
```

3. Verify C1 state from the command line:
```bash
./scripts/verify_c1_state.sh
```

4. Health check endpoints:
```bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/actuator/health
```

5. MinIO console:
```bash
open http://localhost:9001
```
Default local credentials are `minioadmin` / `minioadmin`.

The C1 verifier checks Docker container health, Postgres readiness, MinIO liveness, and whether the application reports the `storage` health component.

6. Run the Week 2 storage vertical:
```bash
./scripts/run_week2_verticals.sh
```

## Next Step

Expand SSE behavior with richer event sequencing and add Week 2 vertical storage/realtime runbooks.

## Learning UI

Open the browser console at `http://localhost:8080/` to register, log in, upload files, inspect health, and watch SSE events in one place.
