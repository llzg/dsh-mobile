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

`POST /api/<namespace>/<method>` — typert "Remote" endpoints used by the GUI
(`commands.*`, `goals.*`, `messageFeedback.*`, `pluginInventory.*`,
`dynamic.*`):

```json
{"args":{"key":"value"}}
```

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

## Handshake & liveness

Connect = both streams open **and** `host.describe` succeeds. On loss:
exponential backoff (500 ms × 2, cap 10 s, jitter), then resync
(`session.list` + per-session history tails). A `stream/error` frame ends
the current generation.

## Trust fence

`Host` header must be loopback or a trusted authority; the app sends no
`Origin`. HTTP 403 = fence rejection (see `docs/COMPATIBILITY.md`).
