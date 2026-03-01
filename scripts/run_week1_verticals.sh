#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="vertical1+$(date +%s)@example.com"
PASSWORD="secret123"

LOG_DIR="docs/verticals/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/week1-$(date +%Y%m%d-%H%M%S).md"

TOKEN=""
FILE_ID=""
IDEM_KEY="week1-idem-1"

write_log_header() {
  cat > "$LOG_FILE" <<LOG
# Week 1 Vertical Test Log

- Date: $(date -u +"%Y-%m-%d %H:%M:%SZ")
- Base URL: $BASE_URL
- Script: scripts/run_week1_verticals.sh

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
  if [ -n "$payload" ]; then
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

request_auth_delete() {
  local url="$1"
  local outfile="$2"
  curl -sS -o "$outfile" -w "%{http_code}" -X DELETE "$url" \
    -H "Authorization: Bearer $TOKEN"
}

request_auth_upload() {
  local outfile="$1"
  local file_path="$2"
  local idem_key="${3:-}"
  if [ -n "$idem_key" ]; then
    curl -sS -o "$outfile" -w "%{http_code}" -X POST "$BASE_URL/api/v1/files" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Idempotency-Key: $idem_key" \
      -F "file=@${file_path};type=text/plain"
  else
    curl -sS -o "$outfile" -w "%{http_code}" -X POST "$BASE_URL/api/v1/files" \
      -H "Authorization: Bearer $TOKEN" \
      -F "file=@${file_path};type=text/plain"
  fi
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

echo "[1/10] Register"
REGISTER_STATUS=$(request_json "POST" "$BASE_URL/api/v1/auth/register" "/tmp/week1_register.json" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
REGISTER_TOKEN=$(extract_token /tmp/week1_register.json)
if [ "$REGISTER_STATUS" != "201" ] || [ -z "$REGISTER_TOKEN" ]; then
  fail_and_exit "1" "Register" \
    "Authentication bootstrap and persistence integrity" \
    "AuthController -> AuthService.register -> UserRepository.save -> JwtService.issueToken" \
    "Validates user creation path and token issuance boundary" \
    "POST /api/v1/auth/register" "$REGISTER_STATUS" "Body: $(cat /tmp/week1_register.json)"
fi
append_step_log "1" "Register" \
  "Authentication bootstrap and persistence integrity" \
  "AuthController -> AuthService.register -> UserRepository.save -> JwtService.issueToken" \
  "Validates user creation path and token issuance boundary" \
  "POST /api/v1/auth/register" "$REGISTER_STATUS" "PASSED" "Token issued for new user"

echo "[2/10] Login"
LOGIN_STATUS=$(request_json "POST" "$BASE_URL/api/v1/auth/login" "/tmp/week1_login.json" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(extract_token /tmp/week1_login.json)
if [ "$LOGIN_STATUS" != "200" ] || [ -z "$TOKEN" ]; then
  fail_and_exit "2" "Login" \
    "Credential verification and token refresh behavior" \
    "AuthController -> AuthService.login -> PasswordHasher.matches -> JwtService.issueToken" \
    "Confirms deterministic auth success path and JWT generation" \
    "POST /api/v1/auth/login" "$LOGIN_STATUS" "Body: $(cat /tmp/week1_login.json)"
fi
append_step_log "2" "Login" \
  "Credential verification and token refresh behavior" \
  "AuthController -> AuthService.login -> PasswordHasher.matches -> JwtService.issueToken" \
  "Confirms deterministic auth success path and JWT generation" \
  "POST /api/v1/auth/login" "$LOGIN_STATUS" "PASSED" "Token length: ${#TOKEN}"

echo "[3/10] Auth required check"
UNAUTH_STATUS=$(request_json "GET" "$BASE_URL/api/v1/files" "/tmp/week1_unauth.json")
if [ "$UNAUTH_STATUS" != "401" ]; then
  fail_and_exit "3" "Auth Required Check" \
    "Security boundary enforcement" \
    "SecurityFilterChain -> JwtAuthenticationFilter -> AuthenticationEntryPoint" \
    "Ensures protected resources reject unauthenticated access" \
    "GET /api/v1/files (no token)" "$UNAUTH_STATUS" "Body: $(cat /tmp/week1_unauth.json)"
fi
append_step_log "3" "Auth Required Check" \
  "Security boundary enforcement" \
  "SecurityFilterChain -> JwtAuthenticationFilter -> AuthenticationEntryPoint" \
  "Ensures protected resources reject unauthenticated access" \
  "GET /api/v1/files (no token)" "$UNAUTH_STATUS" "PASSED" "Returned UNAUTHORIZED as expected"

echo "[4/10] Authenticated preflight check"
AUTH_STATUS=$(request_auth_get "$BASE_URL/api/v1/files" "/tmp/week1_auth_ok.json")
if [ "$AUTH_STATUS" != "200" ]; then
  fail_and_exit "4" "Authenticated Preflight" \
    "JWT acceptance and principal propagation" \
    "JwtAuthenticationFilter -> SecurityContext -> FileMetadataController.list" \
    "Proves token can cross filter boundary into protected controller" \
    "GET /api/v1/files (with token)" "$AUTH_STATUS" "Token length: ${#TOKEN}; Body: $(cat /tmp/week1_auth_ok.json)"
fi
append_step_log "4" "Authenticated Preflight" \
  "JWT acceptance and principal propagation" \
  "JwtAuthenticationFilter -> SecurityContext -> FileMetadataController.list" \
  "Proves token can cross filter boundary into protected controller" \
  "GET /api/v1/files (with token)" "$AUTH_STATUS" "PASSED" "Authenticated request accepted"

echo "[5/10] Create file metadata"
printf 'hello-week1' > /tmp/week1_file_a.txt
CREATE_STATUS=$(request_auth_upload "/tmp/week1_create.json" "/tmp/week1_file_a.txt")
FILE_ID=$(extract_id /tmp/week1_create.json)
if [ "$CREATE_STATUS" != "201" ] || [ -z "$FILE_ID" ]; then
  fail_and_exit "5" "Create File Metadata" \
    "Write-path correctness and metadata persistence" \
    "FileMetadataController.create -> FileMetadataService.create -> FileRepository.save" \
    "Validates create flow and returned metadata contract" \
    "POST /api/v1/files multipart" "$CREATE_STATUS" "Body: $(cat /tmp/week1_create.json)"
fi
append_step_log "5" "Create File Metadata" \
  "Write-path correctness and metadata persistence" \
  "FileMetadataController.create -> FileMetadataService.create -> FileRepository.save" \
  "Validates create flow and returned metadata contract" \
  "POST /api/v1/files multipart" "$CREATE_STATUS" "PASSED" "File ID: $FILE_ID"

echo "[6/10] List files"
LIST_STATUS=$(request_auth_get "$BASE_URL/api/v1/files" "/tmp/week1_list.json")
if [ "$LIST_STATUS" != "200" ]; then
  fail_and_exit "6" "List Files" \
    "Read-path consistency and ownership filtering" \
    "FileMetadataController.list -> FileMetadataService.list -> FileRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc" \
    "Ensures list view is owner-scoped and excludes soft-deleted rows" \
    "GET /api/v1/files" "$LIST_STATUS" "Body: $(cat /tmp/week1_list.json)"
fi
append_step_log "6" "List Files" \
  "Read-path consistency and ownership filtering" \
  "FileMetadataController.list -> FileMetadataService.list -> FileRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc" \
  "Ensures list view is owner-scoped and excludes soft-deleted rows" \
  "GET /api/v1/files" "$LIST_STATUS" "PASSED" "List returned successfully"

echo "[7/10] Idempotency replay"
IDEM_FIRST_STATUS=$(request_auth_upload "/tmp/week1_idem_1.json" "/tmp/week1_file_a.txt" "$IDEM_KEY")
IDEM_SECOND_STATUS=$(request_auth_upload "/tmp/week1_idem_2.json" "/tmp/week1_file_a.txt" "$IDEM_KEY")
IDEM_FIRST_ID=$(extract_id /tmp/week1_idem_1.json)
IDEM_SECOND_ID=$(extract_id /tmp/week1_idem_2.json)
if [ "$IDEM_FIRST_STATUS" != "201" ] || [ "$IDEM_SECOND_STATUS" != "201" ] || [ "$IDEM_FIRST_ID" != "$IDEM_SECOND_ID" ]; then
  fail_and_exit "7" "Idempotency Replay" \
    "Retry-safety for create operations" \
    "FileMetadataController.create -> FileCreateRequestHasher.hash -> IdempotencyService.tryReplay/save" \
    "Prevents duplicate writes for retried identical requests" \
    "POST /api/v1/files with Idempotency-Key: $IDEM_KEY" "$IDEM_SECOND_STATUS" "First: $(cat /tmp/week1_idem_1.json); Second: $(cat /tmp/week1_idem_2.json)"
fi
append_step_log "7" "Idempotency Replay" \
  "Retry-safety for create operations" \
  "FileMetadataController.create -> FileCreateRequestHasher.hash -> IdempotencyService.tryReplay/save" \
  "Prevents duplicate writes for retried identical requests" \
  "POST /api/v1/files with Idempotency-Key: $IDEM_KEY" "$IDEM_SECOND_STATUS" "PASSED; replayed id: $IDEM_SECOND_ID"

echo "[8/10] Idempotency conflict"
printf 'different-payload' > /tmp/week1_file_b.txt
IDEM_CONFLICT_STATUS=$(request_auth_upload "/tmp/week1_idem_conflict.json" "/tmp/week1_file_b.txt" "$IDEM_KEY")
if [ "$IDEM_CONFLICT_STATUS" != "409" ]; then
  fail_and_exit "8" "Idempotency Conflict" \
    "Key misuse detection for semantic safety" \
    "IdempotencyService.tryReplay -> IdempotencyConflictException -> FileExceptionHandler" \
    "Rejects conflicting intent under same idempotency key" \
    "POST /api/v1/files with reused key + different payload" "$IDEM_CONFLICT_STATUS" "Body: $(cat /tmp/week1_idem_conflict.json)"
fi
append_step_log "8" "Idempotency Conflict" \
  "Key misuse detection for semantic safety" \
  "IdempotencyService.tryReplay -> IdempotencyConflictException -> FileExceptionHandler" \
  "Rejects conflicting intent under same idempotency key" \
  "POST /api/v1/files with reused key + different payload" "$IDEM_CONFLICT_STATUS" "PASSED; returned IDEMPOTENCY_CONFLICT"

echo "[9/10] Delete file"
DELETE_STATUS=$(request_auth_delete "$BASE_URL/api/v1/files/$FILE_ID" "/tmp/week1_delete.json")
if [ "$DELETE_STATUS" != "204" ]; then
  fail_and_exit "9" "Delete File" \
    "Ownership enforcement and soft-delete transition" \
    "FileMetadataController.delete -> FileMetadataService.delete -> FileRepository.save" \
    "Validates state transition to deleted without hard row removal" \
    "DELETE /api/v1/files/$FILE_ID" "$DELETE_STATUS" "Body: $(cat /tmp/week1_delete.json)"
fi
append_step_log "9" "Delete File" \
  "Ownership enforcement and soft-delete transition" \
  "FileMetadataController.delete -> FileMetadataService.delete -> FileRepository.save" \
  "Validates state transition to deleted without hard row removal" \
  "DELETE /api/v1/files/$FILE_ID" "$DELETE_STATUS" "PASSED" "Delete completed"

echo "[10/10] Delete non-existent file"
MISSING_STATUS=$(request_auth_delete "$BASE_URL/api/v1/files/00000000-0000-0000-0000-000000000000" "/tmp/week1_missing_delete.json")
if [ "$MISSING_STATUS" != "404" ]; then
  fail_and_exit "10" "Delete Missing File" \
    "Failure-path determinism and stable error semantics" \
    "FileMetadataService.delete -> FileNotFoundException -> FileExceptionHandler" \
    "Ensures error contract remains predictable for absent resources" \
    "DELETE /api/v1/files/00000000-0000-0000-0000-000000000000" "$MISSING_STATUS" "Body: $(cat /tmp/week1_missing_delete.json)"
fi
append_step_log "10" "Delete Missing File" \
  "Failure-path determinism and stable error semantics" \
  "FileMetadataService.delete -> FileNotFoundException -> FileExceptionHandler" \
  "Ensures error contract remains predictable for absent resources" \
  "DELETE /api/v1/files/00000000-0000-0000-0000-000000000000" "$MISSING_STATUS" "PASSED" "Returned FILE_NOT_FOUND"

mark_summary "PASSED"

echo "[done] Success"
echo "Week 1 vertical checks passed"
echo "Detailed log written to: $LOG_FILE"
