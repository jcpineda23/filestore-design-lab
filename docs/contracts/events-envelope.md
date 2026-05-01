# Events Envelope

All Milestone C system events use this shared SSE envelope.

## JSON Shape

```json
{
  "eventId": "01JXYZABC123",
  "eventType": "file.upload.completed",
  "occurredAt": "2026-02-27T18:10:22Z",
  "userId": "usr_01JXYZUSER1",
  "resourceType": "file",
  "resourceId": "fil_01JXYZFILE1",
  "correlationId": "req_01JXYZREQ1",
  "data": {}
}
```

## Field Rules

1. `eventId`
- Unique identifier (ULID preferred).
- Used for deduplication and SSE `id` mapping.

2. `eventType`
- Dot-notated domain event name.
- Versionless in v1 (`file.upload.completed`).

3. `occurredAt`
- ISO-8601 UTC timestamp.

4. `userId`
- Owner/user scope for event routing.

5. `resourceType`
- `file` for file operations.

6. `resourceId`
- File id for file events.

7. `correlationId`
- Propagated from request boundary.
- Used to tie API request, logs, and downstream events.

8. `data`
- Event-specific payload object.

## Transport Mapping

1. SSE
- frame `id` = `eventId`
- frame `event` = `eventType`
- frame `data` = full envelope JSON
- initial connection bootstrap sends `event: connected` with `data: ok`
- reconnects are live-only in v1; missed events are not replayed
- `Last-Event-ID` is not used yet

2. WebSocket
- Deferred beyond Milestone C.
