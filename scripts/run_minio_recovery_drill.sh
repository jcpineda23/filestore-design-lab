#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="recovery+$(date +%s)@example.com"
PASSWORD="secret123"

LOG_DIR="docs/verticals/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/minio-recovery-$(date +%Y%m%d-%H%M%S).md"

TOKEN=""
DOWNLOAD_FILE_ID=""
DELETE_FILE_ID=""
SSE_PID=""
SSE_FILE="/tmp/minio_recovery_sse_$$.log"
DOWNLOAD_SEED_FILE="/tmp/minio_recovery_download_seed_$$.txt"
DELETE_SEED_FILE="/tmp/minio_recovery_delete_seed_$$.txt"
OUTAGE_UPLOAD_FILE="/tmp/minio_recovery_outage_$$.txt"
RECOVERY_UPLOAD_FILE="/tmp/minio_recovery_after_restart_$$.txt"
RECOVERED_DOWNLOAD_FILE="/tmp/minio_recovery_download_after_restart_$$.txt"
MINIO_STOPPED_BY_SCRIPT="false"

write_log_header() {
  cat > "$LOG_FILE" <<LOG
# MinIO Recovery Drill Log

- Date: $(date -u +"%Y-%m-%d %H:%M:%SZ")
- Base URL: $BASE_URL
- Script: scripts/run_minio_recovery_drill.sh

## Summary

- Status: RUNNING

## Step Logs
LOG
}

append_step_log() {
  local step="$1"
  local title="$2"
  local concept="$3"
  local class_flow="$4"
  local why="$5"
  local request_summary="$6"
  local status_code="$7"
  local result="$8"
  local notes="$9"

  cat >> "$LOG_FILE" <<LOG
### ${step}. ${title}

- Result: ${result}
- HTTP Status: ${status_code}
- Concept Exercised: ${concept}
- Request: ${request_summary}
- Class Flow: ${class_flow}
- Why It Matters: ${why}
- Notes: ${notes}

LOG
}

mark_summary() {
  local status="$1"
  sed -i '' "s/- Status: RUNNING/- Status: ${status}/" "$LOG_FILE"
}

cleanup() {
  if [[ -n "${SSE_PID:-}" ]] && kill -0 "$SSE_PID" >/dev/null 2>&1; then
    kill "$SSE_PID" >/dev/null 2>&1 || true
    wait "$SSE_PID" 2>/dev/null || true
  fi
  if [[ "$MINIO_STOPPED_BY_SCRIPT" == "true" ]]; then
    docker compose up -d minio >/dev/null 2>&1 || true
  fi
  rm -f "$SSE_FILE" "$DOWNLOAD_SEED_FILE" "$DELETE_SEED_FILE" \
    "$OUTAGE_UPLOAD_FILE" "$RECOVERY_UPLOAD_FILE" "$RECOVERED_DOWNLOAD_FILE" \
    /tmp/minio_recovery_register.json /tmp/minio_recovery_login.json \
    /tmp/minio_recovery_download_seed_create.json /tmp/minio_recovery_delete_seed_create.json \
    /tmp/minio_recovery_outage_create.json /tmp/minio_recovery_outage_download.json \
    /tmp/minio_recovery_outage_delete.json /tmp/minio_recovery_health_down.json \
    /tmp/minio_recovery_health_up.txt /tmp/minio_recovery_after_restart_create.json \
    /tmp/minio_recovery_list_after_restart.json /tmp/minio_recovery_delete_after_restart.json
}

trap cleanup EXIT

extract_token() {
  local file="$1"
  sed -n 's/.*"token":"\([^"]*\)".*/\1/p' "$file"
}

extract_id() {
  local file="$1"
  sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$file"
}

request_json() {
  local method="$1"
  local url="$2"
  local outfile="$3"
  local payload="${4:-}"
  if [[ -n "$payload" ]]; then
    curl -sS -o "$outfile" -w "%{http_code}" -X "$method" "$url" \
      -H "Content-Type: application/json" \
      -d "$payload"
  else
    curl -sS -o "$outfile" -w "%{http_code}" -X "$method" "$url"
  fi
}

request_auth_upload() {
  local outfile="$1"
  local file_path="$2"
  local correlation_id="$3"
  curl -sS -o "$outfile" -w "%{http_code}" -X POST "$BASE_URL/api/v1/files" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Correlation-Id: $correlation_id" \
    -F "file=@${file_path};type=text/plain"
}

request_auth_delete() {
  local url="$1"
  local outfile="$2"
  local correlation_id="$3"
  curl -sS -o "$outfile" -w "%{http_code}" -X DELETE "$url" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Correlation-Id: $correlation_id"
}

wait_for_sse_event() {
  local event_name="$1"
  local attempts=20
  local i=1
  while [[ $i -le $attempts ]]; do
    if grep -q "event: ${event_name}" "$SSE_FILE" 2>/dev/null; then
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  return 1
}

wait_for_storage_down() {
  local attempts=15
  local i=1
  while [[ $i -le $attempts ]]; do
    curl -sS "$BASE_URL/actuator/health" > /tmp/minio_recovery_health_down.json
    if grep -q '"status":"DOWN"' /tmp/minio_recovery_health_down.json; then
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  return 1
}

fail_and_exit() {
  local step="$1"
  local title="$2"
  local concept="$3"
  local class_flow="$4"
  local why="$5"
  local request_summary="$6"
  local status_code="$7"
  local notes="$8"

  append_step_log "$step" "$title" "$concept" "$class_flow" "$why" "$request_summary" "$status_code" "FAILED" "$notes"
  mark_summary "FAILED"
  echo "Failure in step ${step}: ${title}"
  echo "See log: $LOG_FILE"
  exit 1
}

write_log_header

echo "[1/10] Verify platform readiness"
if ./scripts/verify_c1_state.sh >/tmp/minio_recovery_health_up.txt 2>&1; then
  append_step_log "1" "Verify Platform Readiness" \
    "Dependency health before a failure drill" \
    "verify_c1_state.sh -> docker compose -> Postgres health -> MinIO health -> Spring actuator" \
    "A recovery drill only means something if the steady state was healthy first" \
    "./scripts/verify_c1_state.sh" "200" "PASSED" "$(tr '\n' ' ' < /tmp/minio_recovery_health_up.txt)"
else
  fail_and_exit "1" "Verify Platform Readiness" \
    "Dependency health before a failure drill" \
    "verify_c1_state.sh -> docker compose -> Postgres health -> MinIO health -> Spring actuator" \
    "A recovery drill only means something if the steady state was healthy first" \
    "./scripts/verify_c1_state.sh" "500" "$(tr '\n' ' ' < /tmp/minio_recovery_health_up.txt)"
fi

echo "[2/10] Register, login, and open SSE"
REGISTER_STATUS=$(request_json "POST" "$BASE_URL/api/v1/auth/register" "/tmp/minio_recovery_register.json" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
LOGIN_STATUS=$(request_json "POST" "$BASE_URL/api/v1/auth/login" "/tmp/minio_recovery_login.json" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(extract_token /tmp/minio_recovery_login.json)
if [[ "$REGISTER_STATUS" != "201" || "$LOGIN_STATUS" != "200" || -z "$TOKEN" ]]; then
  fail_and_exit "2" "Register Login And Open SSE" \
    "Authenticated user-scoped drill setup" \
    "AuthController -> AuthService -> UserRepository -> JwtService" \
    "Recovery behavior must be tested through the same authenticated API surface that real clients use" \
    "POST /api/v1/auth/register and POST /api/v1/auth/login" "$LOGIN_STATUS" \
    "Register: $(cat /tmp/minio_recovery_register.json) Login: $(cat /tmp/minio_recovery_login.json)"
fi
curl -sS -N "$BASE_URL/api/v1/events/stream" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: text/event-stream" > "$SSE_FILE" &
SSE_PID=$!
sleep 2
if ! grep -q "event: connected" "$SSE_FILE" 2>/dev/null; then
  fail_and_exit "2" "Register Login And Open SSE" \
    "Authenticated user-scoped drill setup" \
    "AuthController -> AuthService -> UserRepository -> JwtService -> EventStreamController.stream" \
    "We want the realtime channel open before the drill so outage and recovery events are visible" \
    "GET /api/v1/events/stream" "200" "SSE output: $(cat "$SSE_FILE" 2>/dev/null)"
fi
append_step_log "2" "Register Login And Open SSE" \
  "Authenticated user-scoped drill setup" \
  "AuthController -> AuthService -> UserRepository -> JwtService -> EventStreamController.stream" \
  "We want the realtime channel open before the drill so outage and recovery events are visible" \
  "POST /api/v1/auth/register, POST /api/v1/auth/login, GET /api/v1/events/stream" "200" "PASSED" \
  "Connected event received for user $EMAIL"

echo "[3/10] Seed downloadable file"
printf 'download-seed-%s\n' "$(date +%s)" > "$DOWNLOAD_SEED_FILE"
DOWNLOAD_SEED_STATUS=$(request_auth_upload "/tmp/minio_recovery_download_seed_create.json" "$DOWNLOAD_SEED_FILE" "drill-download-seed")
DOWNLOAD_FILE_ID=$(extract_id /tmp/minio_recovery_download_seed_create.json)
if [[ "$DOWNLOAD_SEED_STATUS" != "201" || -z "$DOWNLOAD_FILE_ID" ]]; then
  fail_and_exit "3" "Seed Downloadable File" \
    "Create a known-good object before the outage" \
    "FileMetadataController.create -> FileMetadataService.create -> ObjectStorageService.putObject" \
    "We need a pre-existing object to prove download still fails cleanly during outage and recovers afterward" \
    "POST /api/v1/files" "$DOWNLOAD_SEED_STATUS" "Body: $(cat /tmp/minio_recovery_download_seed_create.json)"
fi
append_step_log "3" "Seed Downloadable File" \
  "Create a known-good object before the outage" \
  "FileMetadataController.create -> FileMetadataService.create -> ObjectStorageService.putObject" \
  "We need a pre-existing object to prove download still fails cleanly during outage and recovers afterward" \
  "POST /api/v1/files" "$DOWNLOAD_SEED_STATUS" "PASSED" \
  "Download recovery file id: $DOWNLOAD_FILE_ID"

echo "[4/10] Seed deletable file"
printf 'delete-seed-%s\n' "$(date +%s)" > "$DELETE_SEED_FILE"
DELETE_SEED_STATUS=$(request_auth_upload "/tmp/minio_recovery_delete_seed_create.json" "$DELETE_SEED_FILE" "drill-delete-seed")
DELETE_FILE_ID=$(extract_id /tmp/minio_recovery_delete_seed_create.json)
if [[ "$DELETE_SEED_STATUS" != "201" || -z "$DELETE_FILE_ID" ]]; then
  fail_and_exit "4" "Seed Deletable File" \
    "Create a second known-good object before the outage" \
    "FileMetadataController.create -> FileMetadataService.create -> ObjectStorageService.putObject" \
    "We keep delete validation separate from download validation so we can prove both recover cleanly" \
    "POST /api/v1/files" "$DELETE_SEED_STATUS" "Body: $(cat /tmp/minio_recovery_delete_seed_create.json)"
fi
append_step_log "4" "Seed Deletable File" \
  "Create a second known-good object before the outage" \
  "FileMetadataController.create -> FileMetadataService.create -> ObjectStorageService.putObject" \
  "We keep delete validation separate from download validation so we can prove both recover cleanly" \
  "POST /api/v1/files" "$DELETE_SEED_STATUS" "PASSED" \
  "Delete recovery file id: $DELETE_FILE_ID"

echo "[5/10] Stop MinIO and wait for storage health to go DOWN"
docker compose stop minio >/dev/null
MINIO_STOPPED_BY_SCRIPT="true"
if ! wait_for_storage_down; then
  fail_and_exit "5" "Stop MinIO" \
    "Outage detection through health signaling" \
    "docker compose stop minio -> StorageHealthIndicator -> /actuator/health" \
    "Operators need a quick, externally visible signal that blob storage is degraded" \
    "docker compose stop minio and GET /actuator/health" "200" "$(cat /tmp/minio_recovery_health_down.json 2>/dev/null)"
fi
append_step_log "5" "Stop MinIO" \
  "Outage detection through health signaling" \
  "docker compose stop minio -> StorageHealthIndicator -> /actuator/health" \
  "Operators need a quick, externally visible signal that blob storage is degraded" \
  "docker compose stop minio and GET /actuator/health" "200" "PASSED" \
  "Actuator health reported DOWN while MinIO was stopped"

echo "[6/10] Verify storage-backed APIs fail during outage"
printf 'outage-upload-%s\n' "$(date +%s)" > "$OUTAGE_UPLOAD_FILE"
OUTAGE_CREATE_STATUS=$(request_auth_upload "/tmp/minio_recovery_outage_create.json" "$OUTAGE_UPLOAD_FILE" "drill-outage-upload")
OUTAGE_DOWNLOAD_STATUS=$(curl -sS -o /tmp/minio_recovery_outage_download.json -w "%{http_code}" \
  "$BASE_URL/api/v1/files/$DOWNLOAD_FILE_ID/download" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: drill-outage-download")
OUTAGE_DELETE_STATUS=$(request_auth_delete "$BASE_URL/api/v1/files/$DELETE_FILE_ID" "/tmp/minio_recovery_outage_delete.json" "drill-outage-delete")
if [[ "$OUTAGE_CREATE_STATUS" != "503" || "$OUTAGE_DOWNLOAD_STATUS" != "503" || "$OUTAGE_DELETE_STATUS" != "503" ]] \
  || ! grep -q '"code":"STORAGE_UNAVAILABLE"' /tmp/minio_recovery_outage_create.json \
  || ! grep -q '"code":"STORAGE_UNAVAILABLE"' /tmp/minio_recovery_outage_download.json \
  || ! grep -q '"code":"STORAGE_UNAVAILABLE"' /tmp/minio_recovery_outage_delete.json; then
  fail_and_exit "6" "Verify Storage Backed API Failure Mode" \
    "Graceful degradation during dependency outage" \
    "FileMetadataService.create/download/delete -> StorageUnavailableException -> FileExceptionHandler" \
    "The system should fail fast and consistently when blob storage is unavailable" \
    "Upload, download, and delete while MinIO is down" "$OUTAGE_CREATE_STATUS/$OUTAGE_DOWNLOAD_STATUS/$OUTAGE_DELETE_STATUS" \
    "Create: $(cat /tmp/minio_recovery_outage_create.json 2>/dev/null); Download: $(cat /tmp/minio_recovery_outage_download.json 2>/dev/null); Delete: $(cat /tmp/minio_recovery_outage_delete.json 2>/dev/null)"
fi
append_step_log "6" "Verify Storage Backed API Failure Mode" \
  "Graceful degradation during dependency outage" \
  "FileMetadataService.create/download/delete -> StorageUnavailableException -> FileExceptionHandler" \
  "The system should fail fast and consistently when blob storage is unavailable" \
  "Upload, download, and delete while MinIO is down" "$OUTAGE_CREATE_STATUS/$OUTAGE_DOWNLOAD_STATUS/$OUTAGE_DELETE_STATUS" "PASSED" \
  "All three storage-backed operations returned 503 STORAGE_UNAVAILABLE"

echo "[7/10] Verify outage SSE signals"
UPLOAD_FAILED_OK="no"
DELETE_FAILED_OK="no"
if wait_for_sse_event "file.upload.failed"; then
  UPLOAD_FAILED_OK="yes"
fi
if wait_for_sse_event "file.delete.failed"; then
  DELETE_FAILED_OK="yes"
fi
if [[ "$UPLOAD_FAILED_OK" != "yes" || "$DELETE_FAILED_OK" != "yes" ]]; then
  fail_and_exit "7" "Verify Outage SSE Signals" \
    "Realtime visibility into storage failures" \
    "FileMetadataService.publish -> DomainEventFactory.fileEvent -> UserEventPublisher.publish -> SseEmitter.send" \
    "Clients should observe failure transitions without polling for them" \
    "SSE stream for file.upload.failed and file.delete.failed" "200" "SSE output: $(cat "$SSE_FILE" 2>/dev/null)"
fi
append_step_log "7" "Verify Outage SSE Signals" \
  "Realtime visibility into storage failures" \
  "FileMetadataService.publish -> DomainEventFactory.fileEvent -> UserEventPublisher.publish -> SseEmitter.send" \
  "Clients should observe failure transitions without polling for them" \
  "SSE stream for file.upload.failed and file.delete.failed" "200" "PASSED" \
  "Observed file.upload.failed and file.delete.failed during the outage"

echo "[8/10] Restart MinIO and re-verify readiness"
docker compose up -d minio >/dev/null
MINIO_STOPPED_BY_SCRIPT="false"
if ./scripts/verify_c1_state.sh >/tmp/minio_recovery_health_up.txt 2>&1; then
  append_step_log "8" "Restart MinIO" \
    "Dependency recovery after an outage" \
    "docker compose up -d minio -> verify_c1_state.sh -> StorageHealthIndicator" \
    "We want proof that the app sees storage as healthy again without being restarted" \
    "docker compose up -d minio and ./scripts/verify_c1_state.sh" "200" "PASSED" \
    "$(tr '\n' ' ' < /tmp/minio_recovery_health_up.txt)"
else
  fail_and_exit "8" "Restart MinIO" \
    "Dependency recovery after an outage" \
    "docker compose up -d minio -> verify_c1_state.sh -> StorageHealthIndicator" \
    "We want proof that the app sees storage as healthy again without being restarted" \
    "docker compose up -d minio and ./scripts/verify_c1_state.sh" "500" "$(tr '\n' ' ' < /tmp/minio_recovery_health_up.txt)"
fi

echo "[9/10] Verify upload, download, and delete recover without app restart"
printf 'recovery-upload-%s\n' "$(date +%s)" > "$RECOVERY_UPLOAD_FILE"
RECOVERY_CREATE_STATUS=$(request_auth_upload "/tmp/minio_recovery_after_restart_create.json" "$RECOVERY_UPLOAD_FILE" "drill-recovery-upload")
RECOVERY_DOWNLOAD_STATUS=$(curl -sS -o "$RECOVERED_DOWNLOAD_FILE" -w "%{http_code}" \
  "$BASE_URL/api/v1/files/$DOWNLOAD_FILE_ID/download" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: drill-recovery-download")
RECOVERY_DELETE_STATUS=$(request_auth_delete "$BASE_URL/api/v1/files/$DELETE_FILE_ID" "/tmp/minio_recovery_delete_after_restart.json" "drill-recovery-delete")
if [[ "$RECOVERY_CREATE_STATUS" != "201" || "$RECOVERY_DOWNLOAD_STATUS" != "200" || "$RECOVERY_DELETE_STATUS" != "204" ]] \
  || ! cmp -s "$DOWNLOAD_SEED_FILE" "$RECOVERED_DOWNLOAD_FILE"; then
  fail_and_exit "9" "Verify API Recovery" \
    "Restart-and-recover behavior without app changes" \
    "FileMetadataService.create/download/delete after MinIO recovery" \
    "This is the core Milestone C operational promise: the app should resume normal storage behavior once MinIO is back" \
    "Upload, download, and delete after MinIO restart" "$RECOVERY_CREATE_STATUS/$RECOVERY_DOWNLOAD_STATUS/$RECOVERY_DELETE_STATUS" \
    "Create: $(cat /tmp/minio_recovery_after_restart_create.json 2>/dev/null); Delete: $(cat /tmp/minio_recovery_delete_after_restart.json 2>/dev/null)"
fi
append_step_log "9" "Verify API Recovery" \
  "Restart-and-recover behavior without app changes" \
  "FileMetadataService.create/download/delete after MinIO recovery" \
  "This is the core Milestone C operational promise: the app should resume normal storage behavior once MinIO is back" \
  "Upload, download, and delete after MinIO restart" "$RECOVERY_CREATE_STATUS/$RECOVERY_DOWNLOAD_STATUS/$RECOVERY_DELETE_STATUS" "PASSED" \
  "Upload succeeded, the pre-outage download bytes matched, and the previously DELETE_FAILED file could be deleted"

echo "[10/10] Verify recovered user view and delete completion signal"
LIST_AFTER_RESTART_STATUS=$(curl -sS -o /tmp/minio_recovery_list_after_restart.json -w "%{http_code}" \
  "$BASE_URL/api/v1/files" \
  -H "Authorization: Bearer $TOKEN")
DELETE_COMPLETED_OK="no"
if wait_for_sse_event "file.deleted"; then
  DELETE_COMPLETED_OK="yes"
fi
if [[ "$LIST_AFTER_RESTART_STATUS" != "200" || "$DELETE_COMPLETED_OK" != "yes" ]] \
  || grep -q "\"id\":\"$DELETE_FILE_ID\"" /tmp/minio_recovery_list_after_restart.json; then
  fail_and_exit "10" "Verify Recovered User View" \
    "Final metadata consistency after recovery" \
    "FileMetadataController.list -> FileMetadataService.list -> FileRepository and UserEventPublisher.publish" \
    "A clean recovery should end with the failed-delete file gone from the user view and a delete completion event emitted" \
    "GET /api/v1/files and SSE file.deleted after retry delete" "$LIST_AFTER_RESTART_STATUS" \
    "List: $(cat /tmp/minio_recovery_list_after_restart.json 2>/dev/null); SSE: $(cat "$SSE_FILE" 2>/dev/null)"
fi
append_step_log "10" "Verify Recovered User View" \
  "Final metadata consistency after recovery" \
  "FileMetadataController.list -> FileMetadataService.list -> FileRepository and UserEventPublisher.publish" \
  "A clean recovery should end with the failed-delete file gone from the user view and a delete completion event emitted" \
  "GET /api/v1/files and SSE file.deleted after retry delete" "$LIST_AFTER_RESTART_STATUS" "PASSED" \
  "The retried delete completed, file.deleted was observed, and the deleted file no longer appears in the user list"

mark_summary "PASSED"

echo "[done] Success"
echo "MinIO recovery drill passed"
echo "Detailed log written to: $LOG_FILE"
