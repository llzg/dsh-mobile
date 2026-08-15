# Protocol notes

What DSH Mobile speaks, in one page. Authoritative shapes live in the harness
repository (`packages/host/apiproxy/src/api/*`); this document records the
subset the app implements.

## Envelopes

All JSON. `rpcId` is a UUID minted by the initiator and echoed.

### Client → server (HTTP POST)

`POST /api/<method>` — unary calls:

```json
{"type":"client-request","rpcId":"<uuid>","method":"session.list","payload":{}}
```

`POST /api/respond` — answers to server-initiated requests (approvals,
questions):

```json
{"type":"client-response","rpcId":"<server rpcId>","result":{"ok":true,"value":{}}}
```

`POST /api/<namespace>/<method>` — typert "Remote" gateway endpoints used by the
GUI (`commands/*`, `goals/*`, `pluginInventory/*`, and — depending on the
build — `messageFeedback/*`, `dynamic/*`).

These share the ordinary envelope: `{"args": …}` is the **payload**, not the
body, and the envelope's `method` must equal the path. Args are a named object
whose keys must match the remote descriptor exactly; a session-addressed method
takes `agentId`.

```json
{"type":"client-request","rpcId":"<uuid>","method":"commands/list",
 "payload":{"args":{"agentId":"<sessionId>"}}}
```

A path no gateway claims answers **404**, and the trust fence answers **403** —
both mean "this build does not offer that", not "the connection is broken", so
the client maps them to `capability-unavailable` / `forbidden` and hides the
feature instead of reporting a failure.

`commands/execute` (`{"args":{"agentId","line"}}`) is the **only** command write
path. `session.prompt` does not inspect its content — a leading-slash prompt
reaches the model as ordinary user text — so the client adjudicates the draft
against `commands/list` before sending and only calls `session.prompt` when the
line names no registered command. That miss is load-bearing: a `/name` line the
catalog does not claim is how a *skill* is invoked, and the host's pre-step
boundary resolves it. The remote answers with no `value` when the line parses to
no command; since the codec folds an absent value into `{}`, the discriminator is
the presence of `commandId`.

The `command` slot on `session.prompt`'s response, and the `unknown-command` /
`command-error` codes, are dead schema the host never populates.

### Downloads (no envelope)

`GET /api/session.export?sessionId=<id>[&includeDescendants=true]` streams the
session-log ZIP as an attachment (`Content-Disposition: attachment;
filename="dsh-session-<id>.zip"`). It is answered directly, not through an RPC.

### Server → client

Unary response (HTTP 200):

```json
{"type":"server-response","rpcId":"<same>","result":{"ok":true,"value":{}}}
{"type":"server-response","rpcId":"<same>","result":{"ok":false,"error":{"code":"agent-busy","message":"...","details":{}}}}
```

Respond receipt:

```json
{"accepted":true} | {"accepted":false,"reason":"not-pending"|"bad-response"}
```

### Event streams (WebSocket, downlink-only)

`/api/events.mux` (session events, approvals, questions, queues, jobs,
projections) and `/api/events.host` (session/workspace registry frames).
The client must never send data — doing so closes the socket (1008).

```json
{"type":"server-request","rpcId":"<uuid>","method":"session/event","payload":{"sessionId":"...","event":{"type":"turn/end","seq":4,"time":5,"data":{"turn":1,"reason":{"kind":"completed"}}}}}
```

## Session projections

Several facts the UI needs never arrive as an RPC result — they are pushed as
`session/projection` frames and repeated in the `session.history` tail block:

| Key | Carries |
|---|---|
| `permissions` | `{options:[{value,name,description?}], currentValue}` — the preset picker, read-only; the write side is `/permission <value>` |
| `sessionStats` | `{turns, steps, llmMs, toolMs, ttftMs, ttftSteps, decodeMs, decodeTokens}` — `ttftMs` is a **sum** over `ttftSteps`, and throughput must be derived from `decodeTokens / decodeMs` |
| `tokenUsage` | `{uncachedInputTokens, outputTokens, cacheReadTokens, cacheWriteTokens}` |
| `contextPressure` / `contextBreakdown` | context-window occupancy, and what fills it |
| `imageLimits` | the host's own attachment bounds |
| `goal`, `todos`, `plan`, `title`, `sessionListMetadata` | the docks and list metadata |

An absent key means the harness composes no such service; clients hide the
control rather than showing a dead one.

## Handshake & liveness

Connect = both streams open **and** `host.describe` succeeds. On loss:
exponential backoff (500 ms × 2, cap 10 s, jitter), then resync
(`session.list` + per-session history tails). A `stream/error` frame ends
the current generation.

## Trust fence

`Host` header must be loopback or a trusted authority; the app sends no
`Origin`. HTTP 403 = fence rejection (see `docs/COMPATIBILITY.md`).
