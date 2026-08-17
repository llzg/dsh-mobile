# Compatibility

DSH Mobile speaks the DeepSeek Harness **web client protocol** (the JSON-RPC
surface the harness GUI itself consumes over `/api`). That protocol is
internal to the harness (it is not versioned on the wire), so this app pins a
protocol baseline and verifies the harness version from `host.describe`.

| DSH Mobile | Harness version | Status |
|---|---|---|
| 0.1.0 – 0.3.1 | 0.1.0-rc.5 | Supported baseline |

Every app release so far targets the same harness baseline, so this table gains a row
only when the harness version does. The baseline itself is one constant —
`DshCore.PROTOCOL_BASELINE` in `core/src/main/kotlin/com/labteto/dshmobile/core/DshCore.kt`
— and the app shows it in Settings → About next to its own version.

## Version policy

- On connect the app reads `host.describe.version` and compares it with the
  baseline. A different version shows a non-blocking warning.
- If a call fails with an unrecognized shape the app degrades gracefully
  (unknown event types and unknown tool cards render as generic entries; the
  wire JSON is parsed leniently).
- New harness releases are validated with the fixture capture tool
  (`tools/capture`) and the compatibility table above is updated.

## Loopback-only surfaces (by harness design)

These methods are refused for LAN clients (403) and are presented read-only
or hidden:

- `settings.*`, `credentials.*`, `llm.discoverModels`
- `host.pickDirectory`, `host.openPath`
- agent-preset authoring (`agentPreset.read/copy/openDocument/remove`)
