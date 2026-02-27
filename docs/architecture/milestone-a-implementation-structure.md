# Milestone A Implementation Structure

This milestone produces design artifacts only. No runtime business logic should be implemented.

## Artifact Layout

1. Product and scope
- `docs/milestones/milestone-a-plan.md`

2. API contracts
- `docs/contracts/openapi-v1.yaml`
- `docs/contracts/error-contract.md`

3. Event contracts
- `docs/contracts/events-envelope.md`
- `docs/contracts/events-catalog.md`

4. Data model
- `docs/contracts/erd.md`
- `docs/contracts/schema-draft.sql`
- `docs/contracts/file-lifecycle-state-machine.md`

5. Diagrams
- `docs/diagrams/upload-sequence.md`
- `docs/diagrams/download-sequence.md`
- `docs/diagrams/delete-sequence.md`

6. Architecture decisions
- `docs/architecture/adr-001-sse-vs-websocket.md`
- `docs/architecture/adr-002-storage-separation.md`

## Quality Gates

1. Endpoint names, request/response bodies, and status codes are consistent across all docs.
2. Event names and payloads match transport mapping rules.
3. DB schema draft aligns with API/event fields.
4. Diagrams match documented state transitions and failure handling.
5. All Milestone A acceptance criteria are checkable from repository docs.

## Review Checklist Before Milestone B

1. OpenAPI draft reviewed and frozen for v1.
2. Event envelope and event catalog reviewed and frozen for v1.
3. ERD and SQL migration draft reviewed for ownership and idempotency.
4. Sequence diagrams reviewed for upload/download/delete success + failure paths.
5. ADRs approved for transport boundaries and metadata/blob separation.
