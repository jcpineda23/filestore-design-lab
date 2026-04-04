#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

FAILURES=0

print_section() {
  printf '\n== %s ==\n' "$1"
}

record_failure() {
  printf 'FAIL: %s\n' "$1"
  FAILURES=$((FAILURES + 1))
}

record_success() {
  printf 'OK: %s\n' "$1"
}

print_section "Docker Services"
docker compose ps postgres minio

POSTGRES_STATUS="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' filestore-postgres 2>/dev/null || true)"
MINIO_STATUS="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' filestore-minio 2>/dev/null || true)"

if [[ "$POSTGRES_STATUS" == "healthy" ]]; then
  record_success "Postgres container is healthy"
else
  record_failure "Postgres container is not healthy (status=${POSTGRES_STATUS:-missing})"
fi

if [[ "$MINIO_STATUS" == "healthy" ]]; then
  record_success "MinIO container is healthy"
else
  record_failure "MinIO container is not healthy (status=${MINIO_STATUS:-missing})"
fi

print_section "Postgres Readiness"
if docker exec filestore-postgres pg_isready -U filestore -d filestore >/dev/null 2>&1; then
  record_success "Postgres accepts connections for database filestore"
  docker exec filestore-postgres psql -U filestore -d filestore -tAc "select count(*) from information_schema.tables where table_schema = 'public';"
else
  record_failure "Postgres is not accepting connections"
fi

print_section "MinIO Liveness"
if curl -fsS http://localhost:9000/minio/health/live >/dev/null; then
  record_success "MinIO live endpoint is reachable"
else
  record_failure "MinIO live endpoint is not reachable on http://localhost:9000"
fi

print_section "Application Health"
if API_HEALTH="$(curl -fsS http://localhost:8080/api/v1/health 2>/dev/null)"; then
  record_success "Application custom health endpoint is reachable"
  printf '%s\n' "$API_HEALTH"
else
  record_failure "Application custom health endpoint is not reachable on http://localhost:8080/api/v1/health"
fi

if ACTUATOR_HEALTH="$(curl -fsS http://localhost:8080/actuator/health 2>/dev/null)"; then
  record_success "Actuator health endpoint is reachable"
  printf '%s\n' "$ACTUATOR_HEALTH"
  if [[ "$ACTUATOR_HEALTH" == *'"storage"'* ]]; then
    record_success "Actuator health includes storage component"
  else
    record_failure "Actuator health does not include the storage component"
  fi
else
  record_failure "Actuator health endpoint is not reachable on http://localhost:8080/actuator/health"
fi

print_section "Summary"
if [[ "$FAILURES" -eq 0 ]]; then
  printf 'C1 verification passed. Containerized Postgres and MinIO are healthy, and the app can report infra state.\n'
else
  printf 'C1 verification finished with %d failure(s).\n' "$FAILURES"
  exit 1
fi
