# Compatibility

DSH Mobile speaks the DeepSeek Harness **web client protocol** (the JSON-RPC
surface the harness GUI itself consumes over `/api`). That protocol is internal
to the harness and is not versioned on the wire, so this app pins a protocol
baseline: the harness release its DTOs and call shapes were ported from and
checked against.

| DSH Mobile | Harness version | Status |
|---|---|---|
| 0.4.0 | 0.1.0-rc.7 | Supported baseline |
| 0.1.0 – 0.3.1 | 0.1.0-rc.5 | Previous baseline |

The baseline is one constant — `DshCore.PROTOCOL_BASELINE` in
`core/src/main/kotlin/com/labteto/dshmobile/core/DshCore.kt` — and the app shows
it in Settings → About next to its own version.

## Version policy

The baseline says what was tested. It is not a gate, and the app does not compare
it against `host.describe.version` or warn when the two differ — it could not say
what would break if it did. The harness releases far more often than this client,
and most of those releases leave the client surface untouched: rc.5 → rc.7 added
no RPC method, no event type, no projection key and no slash command. A banner
firing on every harness that is not one exact string would be noise on every
session, and would still be silent about the changes that actually matter.

What the app does instead:

- **Degrades on shape, not on version.** Unknown event types, frame kinds, tool
  cards and content blocks fall back to passthroughs rather than failing, and
  unknown keys are ignored. A build that composes no such capability answers 404
  or 403, which the client reads as "this build does not offer that" and hides
  the control instead of reporting a failure.
- **Shows the harness's own version** wherever a host appears — the connect list,
  the details panel, and Settings → Harness — so a mismatch is visible where it
  is useful rather than announced as an alarm.
- **Re-checks on each harness release** with the fixture capture tool
  (`tools/capture`), and moves the baseline once the shapes have been verified.

## Loopback-only surfaces (by harness design)

These methods are refused for LAN clients (403) and are presented read-only
or hidden:

- `settings.*`, `credentials.*`, `llm.discoverModels`
- `host.pickDirectory`, `host.openPath`
- agent-preset authoring (`agentPreset.read/copy/openDocument/remove`)
