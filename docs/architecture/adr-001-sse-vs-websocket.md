# ADR-001: SSE vs WebSocket Responsibilities

## Status
Accepted (Milestone A)

## Context
The system must support real-time updates while teaching practical transport tradeoffs.
We need both server-to-client notifications and bi-directional connection patterns.

## Decision
1. Use SSE for user-scoped, server-originated file lifecycle notifications.
2. Use WebSocket for connection-oriented, bi-directional presence signals.

## Rationale
1. SSE is simpler for one-way event streaming and has native reconnection support.
2. WebSocket is better for heartbeat/presence and interactive signaling.
3. This split keeps each transport focused and easier to reason about in training.

## Consequences
1. Backend event publisher supports fan-out to both transport adapters.
2. Client uses two channels in v1 when it needs both features.
3. SSE replay can be implemented independently from WebSocket session handling.

## Deferred Decisions
1. Whether to unify both channels on a broker-backed subscription abstraction in Milestone E.
2. Whether to expose shared workspace presence beyond current user scope.
