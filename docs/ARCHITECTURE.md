# Architecture

DSH Mobile is a three-module Gradle project (Kotlin 2.0, Compose, Hilt).

```
core/           pure JVM — no Android imports
  wire/         the DeepSeek Harness web-client protocol:
                  envelopes (client-request / server-response /
                  server-request / client-response), the lenient WireJson
                  codec, RpcTransport (OkHttp), WsDownlink (downlink-only
                  WebSockets /api/events.mux + /api/events.host),
                  DshApiClient (52 typed unary methods + respond + typert
                  remotes), ConnectionLoop (readiness handshake
                  host.describe + both streams, exponential backoff)
  wire/dto/     kotlinx.serialization ports of the harness schemas
                  (sessions, host, workspace, skills, goals, settings,
                  credentials, llm, subagents, agent presets, events,
                  frames, tool views) — lenient, merge-extensible
  session/      EventFold: raw session events → ConversationSnapshot
                  (turn/step/message/tool nodes, streaming block assembly,
                  interruption marking, gap detection)
  notify/       CompletionClassifier: turn/goal/approval/question/idle
                  events with dedup keys

app/            Android UI
  connection/   HostsStore (remembered hosts + settings, DataStore),
                  DiscoveryEngine (Wi-Fi subnet sweep + host.describe
                  probe), ConnectionManager (owns the ConnectionLoop,
                  exposes mux/host frame flows), ConnectionService
                  (foreground service), KeepAliveWorker (15-min fallback)
  data/         SessionStore — the live mirror of the harness: session
                  list/workspaces/folds per session, queue/jobs/
                  projections, approvals/questions, subagent catalog
  notify/       NotificationObserver — classifier → channels, dedup,
                  deep links
  ui/           theme (exact DSH design tokens), components (buttons,
                  disclosure rows, state dots, tool cards, markdown,
                  overlays), screens (connect, main shell with
                  Discord-style drawer + details panel, chat, settings)

mock-harness/   Ktor implementation of the /api protocol for tests
tools/capture/  Node recorder of real harness traffic → conformance fixtures
```

## Data flow

1. `ConnectionManager` performs the readiness handshake and pumps the two
   WebSocket downlinks; frames fan out as SharedFlows.
2. `SessionStore` folds session events into `ConversationSnapshot`s
   (incremental), keeps the session/workspace registry from host frames,
   and merges queue/jobs/projection snapshots.
3. Screens observe `StateFlow`s and render; user actions go back through
   `SessionStore` → `DshApiClient` (`POST /api/<method>`, `/api/respond`).
4. `NotificationObserver` classifies frames into completion events and
   posts channel-notifications that deep-link into sessions.

## Key invariants

- The wire layer never crashes on unknown data: unknown keys are ignored,
  unknown event/frame/card types fall back to `Unknown*` passthroughs.
- HTTP status is carrier-only; business failures arrive as `ok: false`
  with a typed error code (see `docs/PROTOCOL.md`).
- The WebSocket streams are **downlink-only** — the client never sends.
- Settings/credentials/host-native methods are loopback-only by harness
  design; over LAN the app surfaces them read-only (see
  `docs/COMPATIBILITY.md`).
- Protocol baseline: harness `0.1.0-rc.5` (`core.DshCore.PROTOCOL_BASELINE`).
