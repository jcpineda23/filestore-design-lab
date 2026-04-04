#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="vertical2+$(date +%s)@example.com"
PASSWORD="secret123"

LOG_DIR="docs/verticals/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/week2-$(date +%Y%m%d-%H%M%S).md"

TOKEN=""
FILE_ID=""
SSE_PID=""
SSE_FILE="/tmp/week2_sse_$$.log"
UPLOAD_FILE="/tmp/week2_upload_$$.txt"
DOWNLOAD_FILE="/tmp/week2_download_$$.txt"

write_log_header() {
  cat > "$LOG_FILE" <<LOG
# Week 2 Vertical Test Log

- Date: $(date -u +"%Y-%m-%d %H:%M:%SZ")
- Base URL: $BASE_URL
- Script: scripts/run_week2_verticals.sh

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
  rm -f "$SSE_FILE" "$UPLOAD_FILE" "$DOWNLOAD_FILE" \
    /tmp/week2_register.json /tmp/week2_login.json /tmp/week2_create.json \
    /tmp/week2_list.json /tmp/week2_verify_c1.txt /tmp/week2_unauth.json
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

request_auth_get() {
  local url="$1"
  local outfile="$2"
  curl -sS -o "$outfile" -w "%{http_code}" "$url" \
    -H "Authorization: Bearer $TOKEN"
}

request_auth_upload() {
  local outfile="$1"
  local file_path="$2"
  curl -sS -o "$outfile" -w "%{http_code}" -X POST "$BASE_URL/api/v1/files" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@${file_path};type=text/plain"
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

write_log_header

echo "[1/7] Verify C1 state"
if ./scripts/verify_c1_state.sh >/tmp/week2_verify_c1.txt 2>&1; then
  append_step_log "1" "Verify C1 State" \
    "Containerized dependency readiness" \
    "verify_c1_state.sh -> docker compose -> Postgres health -> MinIO health -> Spring actuator" \
    "Confirms the platform is healthy before running a real storage vertical" \
    "./scripts/verify_c1_state.sh" "200" "PASSED" "$(tr '\n' ' ' < /tmp/week2_verify_c1.txt)"
else
  fail_and_exit "1" "Verify C1 State" \
    "Containerized dependency readiness" \
    "verify_c1_state.sh -> docker compose -> Postgres health -> MinIO health -> Spring actuator" \
    "Confirms the platform is healthy before running a real storage vertical" \
    "./scripts/verify_c1_state.sh" "500" "$(tr '\n' ' ' < /tmp/week2_verify_c1.txt)"
fi

echo "[2/7] Register and login"
REGISTER_STATUS=$(request_json "POST" "$BASE_URL/api/v1/auth/register" "/tmp/week2_register.json" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
LOGIN_STATUS=$(request_json "POST" "$BASE_URL/api/v1/auth/login" "/tmp/week2_login.json" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(extract_token /tmp/week2_login.json)
if [[ "$REGISTER_STATUS" != "201" || "$LOGIN_STATUS" != "200" || -z "$TOKEN" ]]; then
  fail_and_exit "2" "Register And Login" \
    "Authenticated vertical setup" \
    "AuthController -> AuthService -> UserRepository -> JwtService" \
    "Creates a clean user context for storage and SSE validation" \
    "POST /api/v1/auth/register and POST /api/v1/auth/login" "$LOGIN_STATUS" \
    "Register: $(cat /tmp/week2_register.json) Login: $(cat /tmp/week2_login.json)"
fi
append_step_log "2" "Register And Login" \
  "Authenticated vertical setup" \
  "AuthController -> AuthService -> UserRepository -> JwtService" \
  "Creates a clean user context for storage and SSE validation" \
  "POST /api/v1/auth/register and POST /api/v1/auth/login" "$LOGIN_STATUS" "PASSED" \
  "User email: $EMAIL; token length: ${#TOKEN}"

echo "[3/7] Open SSE stream"
curl -sS -N "$BASE_URL/api/v1/events/stream" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: text/event-stream" > "$SSE_FILE" &
SSE_PID=$!
sleep 2
if ! grep -q "event: connected" "$SSE_FILE" 2>/dev/null; then
  fail_and_exit "3" "Open SSE Stream" \
    "Server-to-client real-time channel bootstrap" \
    "EventStreamController.stream -> UserEventPublisher.subscribe -> SseEmitter" \
    "Proves the user-specific event channel is open before upload starts" \
    "GET /api/v1/events/stream" "200" "SSE output: $(cat "$SSE_FILE" 2>/dev/null)"
fi
append_step_log "3" "Open SSE Stream" \
  "Server-to-client real-time channel bootstrap" \
  "EventStreamController.stream -> UserEventPublisher.subscribe -> SseEmitter" \
  "Proves the user-specific event channel is open before upload starts" \
  "GET /api/v1/events/stream" "200" "PASSED" "Connected event received"

echo "[4/7] Upload real file"
printf 'hello-from-week2-%s\n' "$(date +%s)" > "$UPLOAD_FILE"
CREATE_STATUS=$(request_auth_upload "/tmp/week2_create.json" "$UPLOAD_FILE")
FILE_ID=$(extract_id /tmp/week2_create.json)
if [[ "$CREATE_STATUS" != "201" || -z "$FILE_ID" ]]; then
  fail_and_exit "4" "Upload Real File" \
    "Blob write before metadata finalization" \
    "FileMetadataController.create -> FileMetadataService.create -> ObjectStorageService.putObject -> FileRepository.save -> UserEventPublisher.publish" \
    "Exercises the full storage write path and READY transition" \
    "POST /api/v1/files multipart" "$CREATE_STATUS" "Body: $(cat /tmp/week2_create.json)"
fi
append_step_log "4" "Upload Real File" \
  "Blob write before metadata finalization" \
  "FileMetadataController.create -> FileMetadataService.create -> ObjectStorageService.putObject -> FileRepository.save -> UserEventPublisher.publish" \
  "Exercises the full storage write path and READY transition" \
  "POST /api/v1/files multipart" "$CREATE_STATUS" "PASSED" "File ID: $FILE_ID; response: $(cat /tmp/week2_create.json)"

echo "[5/7] Verify file list and READY state"
LIST_STATUS=$(request_auth_get "$BASE_URL/api/v1/files" "/tmp/week2_list.json")
if [[ "$LIST_STATUS" != "200" ]] || ! grep -q "\"id\":\"$FILE_ID\"" /tmp/week2_list.json || ! grep -q "\"status\":\"READY\"" /tmp/week2_list.json; then
  fail_and_exit "5" "Verify READY State" \
    "Metadata persistence after object storage success" \
    "FileMetadataController.list -> FileMetadataService.list -> FileRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc" \
    "Confirms that the durable metadata view reflects successful blob storage" \
    "GET /api/v1/files" "$LIST_STATUS" "Body: $(cat /tmp/week2_list.json)"
fi
append_step_log "5" "Verify READY State" \
  "Metadata persistence after object storage success" \
  "FileMetadataController.list -> FileMetadataService.list -> FileRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc" \
  "Confirms that the durable metadata view reflects successful blob storage" \
  "GET /api/v1/files" "$LIST_STATUS" "PASSED" "File $FILE_ID is visible and READY"

echo "[6/7] Download and compare bytes"
DOWNLOAD_STATUS=$(curl -sS -o "$DOWNLOAD_FILE" -w "%{http_code}" \
  "$BASE_URL/api/v1/files/$FILE_ID/download" \
  -H "Authorization: Bearer $TOKEN")
if [[ "$DOWNLOAD_STATUS" != "200" ]] || ! cmp -s "$UPLOAD_FILE" "$DOWNLOAD_FILE"; then
  fail_and_exit "6" "Download And Compare Bytes" \
    "Read-after-write consistency across object storage" \
    "FileMetadataController.download -> FileMetadataService.download -> ObjectStorageService.getObject" \
    "Validates that the bytes returned to clients match what was stored" \
    "GET /api/v1/files/$FILE_ID/download" "$DOWNLOAD_STATUS" \
    "Uploaded: $(cat "$UPLOAD_FILE" 2>/dev/null); Downloaded: $(cat "$DOWNLOAD_FILE" 2>/dev/null)"
fi
append_step_log "6" "Download And Compare Bytes" \
  "Read-after-write consistency across object storage" \
  "FileMetadataController.download -> FileMetadataService.download -> ObjectStorageService.getObject" \
  "Validates that the bytes returned to clients match what was stored" \
  "GET /api/v1/files/$FILE_ID/download" "$DOWNLOAD_STATUS" "PASSED" \
  "Uploaded and downloaded bytes match exactly"

echo "[7/7] Verify SSE upload lifecycle"
STARTED_OK="no"
PROGRESS_OK="no"
COMPLETED_OK="no"
if wait_for_sse_event "file.upload.started"; then
  STARTED_OK="yes"
fi
if wait_for_sse_event "file.upload.progress"; then
  PROGRESS_OK="yes"
fi
if wait_for_sse_event "file.upload.completed"; then
  COMPLETED_OK="yes"
fi

if [[ "$STARTED_OK" != "yes" || "$PROGRESS_OK" != "yes" || "$COMPLETED_OK" != "yes" ]]; then
  fail_and_exit "7" "Verify SSE Upload Lifecycle" \
    "Real-time event visibility for write progress" \
    "FileMetadataService.publish -> DomainEventFactory.fileEvent -> UserEventPublisher.publish -> SseEmitter.send" \
    "Ensures clients can observe upload lifecycle transitions without polling" \
    "SSE stream for file.upload.started/progress/completed" "200" "SSE output: $(cat "$SSE_FILE" 2>/dev/null)"
fi
append_step_log "7" "Verify SSE Upload Lifecycle" \
  "Real-time event visibility for write progress" \
  "FileMetadataService.publish -> DomainEventFactory.fileEvent -> UserEventPublisher.publish -> SseEmitter.send" \
  "Ensures clients can observe upload lifecycle transitions without polling" \
  "SSE stream for file.upload.started/progress/completed" "200" "PASSED" \
  "Observed started=$STARTED_OK progress=$PROGRESS_OK completed=$COMPLETED_OK"

mark_summary "PASSED"

echo "[done] Success"
echo "Week 2 vertical checks passed"
echo "Detailed log written to: $LOG_FILE"
