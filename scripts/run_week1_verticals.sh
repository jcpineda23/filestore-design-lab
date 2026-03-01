#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="vertical1+$(date +%s)@example.com"
PASSWORD="secret123"

echo "[1/9] Register"
REGISTER_BODY=$(curl -sS -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(printf '%s' "$REGISTER_BODY" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "Register failed: $REGISTER_BODY"; exit 1; }

echo "[2/9] Auth required check"
UNAUTH_STATUS=$(curl -sS -o /tmp/week1_unauth.json -w "%{http_code}" "$BASE_URL/api/v1/files")
[ "$UNAUTH_STATUS" = "401" ] || { echo "Expected 401, got $UNAUTH_STATUS"; cat /tmp/week1_unauth.json; exit 1; }

echo "[3/9] Create file metadata"
printf 'hello-week1' > /tmp/week1_file_a.txt
CREATE_STATUS=$(curl -sS -o /tmp/week1_create.json -w "%{http_code}" -X POST "$BASE_URL/api/v1/files" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/week1_file_a.txt;type=text/plain")
[ "$CREATE_STATUS" = "201" ] || { echo "Expected 201, got $CREATE_STATUS"; cat /tmp/week1_create.json; exit 1; }
FILE_ID=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' /tmp/week1_create.json)
[ -n "$FILE_ID" ] || { echo "Could not parse file id"; cat /tmp/week1_create.json; exit 1; }

echo "[4/9] List files"
LIST_STATUS=$(curl -sS -o /tmp/week1_list.json -w "%{http_code}" "$BASE_URL/api/v1/files" \
  -H "Authorization: Bearer $TOKEN")
[ "$LIST_STATUS" = "200" ] || { echo "Expected 200, got $LIST_STATUS"; cat /tmp/week1_list.json; exit 1; }

echo "[5/9] Idempotency replay"
IDEM_KEY="week1-idem-1"
IDEM_FIRST=$(curl -sS -o /tmp/week1_idem_1.json -w "%{http_code}" -X POST "$BASE_URL/api/v1/files" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -F "file=@/tmp/week1_file_a.txt;type=text/plain")
[ "$IDEM_FIRST" = "201" ] || { echo "Expected 201, got $IDEM_FIRST"; cat /tmp/week1_idem_1.json; exit 1; }
IDEM_SECOND=$(curl -sS -o /tmp/week1_idem_2.json -w "%{http_code}" -X POST "$BASE_URL/api/v1/files" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -F "file=@/tmp/week1_file_a.txt;type=text/plain")
[ "$IDEM_SECOND" = "201" ] || { echo "Expected 201 replay, got $IDEM_SECOND"; cat /tmp/week1_idem_2.json; exit 1; }

echo "[6/9] Idempotency conflict"
printf 'different-payload' > /tmp/week1_file_b.txt
IDEM_CONFLICT=$(curl -sS -o /tmp/week1_idem_conflict.json -w "%{http_code}" -X POST "$BASE_URL/api/v1/files" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -F "file=@/tmp/week1_file_b.txt;type=text/plain")
[ "$IDEM_CONFLICT" = "409" ] || { echo "Expected 409, got $IDEM_CONFLICT"; cat /tmp/week1_idem_conflict.json; exit 1; }

echo "[7/9] Delete file"
DELETE_STATUS=$(curl -sS -o /tmp/week1_delete.json -w "%{http_code}" -X DELETE "$BASE_URL/api/v1/files/$FILE_ID" \
  -H "Authorization: Bearer $TOKEN")
[ "$DELETE_STATUS" = "204" ] || { echo "Expected 204, got $DELETE_STATUS"; cat /tmp/week1_delete.json; exit 1; }

echo "[8/9] Delete non-existent file"
MISSING_STATUS=$(curl -sS -o /tmp/week1_missing_delete.json -w "%{http_code}" -X DELETE "$BASE_URL/api/v1/files/00000000-0000-0000-0000-000000000000" \
  -H "Authorization: Bearer $TOKEN")
[ "$MISSING_STATUS" = "404" ] || { echo "Expected 404, got $MISSING_STATUS"; cat /tmp/week1_missing_delete.json; exit 1; }

echo "[9/9] Success"
echo "Week 1 vertical checks passed"
