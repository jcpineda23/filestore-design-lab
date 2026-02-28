# FileStore Design Lab (Java)

A learning-focused Java project to practice core system design concepts by building a file storage application with authentication, REST APIs, SSE, and WebSocket support.

## Current Phase
Milestone B in progress.

- Milestone A: system design blueprint (requirements, APIs, event contracts, DB schema, diagrams).
- Milestone B: auth + metadata implementation.
  - B1 complete: Spring Boot bootstrap + profiles + health endpoint + Docker Compose.
  - B2 complete: Flyway migration for `users`, `files`, `idempotency_keys`.

Application features beyond bootstrap/migrations are not implemented yet.

## Repository Structure

- `docs/milestones/milestone-a-plan.md`: exact Milestone A plan.
- `docs/milestones/milestone-b-plan.md`: exact Milestone B plan.
- `docs/architecture/milestone-a-implementation-structure.md`: implementation structure for Milestone A artifacts.
- `docs/contracts/`: API/event/schema contract documents.
- `docs/diagrams/`: sequence diagrams and architecture visuals.
- `src/main/java/`: reserved for future implementation.
- `src/test/java/`: reserved for future tests.

## Tech Baseline (planned)

- Java 21
- Spring Boot
- PostgreSQL
- MinIO (S3-compatible)
- Docker Compose

## Run Locally

1. Start PostgreSQL:
```bash
docker compose up -d
```

2. Run app:
```bash
DB_URL=jdbc:postgresql://localhost:5433/filestore ./mvnw spring-boot:run
```

If Maven Wrapper is not present yet, use:
```bash
DB_URL=jdbc:postgresql://localhost:5433/filestore mvn spring-boot:run
```

3. Health check:
```bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/actuator/health
```

## Next Step

Implement B3-B5 (auth domain, JWT security, register/login endpoints).
