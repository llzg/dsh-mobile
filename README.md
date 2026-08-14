# DSH Mobile — DeepSeek Harness Remote

> An open-source Android companion that puts your **DeepSeek Harness** in
> your pocket. Drive sessions, review plans and goals, answer approvals and
> questions, and get notified when the harness finishes — from your phone,
> over your local network.

DSH Mobile is an **unofficial companion app** for the
[DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness) (MIT),
mirroring its web GUI feature-for-feature in the harness's own visual
language. Android only, Kotlin + Jetpack Compose.

## Features

- **Connect effortlessly** — auto-discovers a harness running on your Wi-Fi
  (active subnet scan + readiness handshake), remembers hosts, supports
  manual `host:port` entry, loopback for same-device setups, and
  auto-connect toggles (last used / LAN / same device).
- **Discord-style navigation** — swipe right from the left edge to open the
  workspace-grouped chat list, swipe left to close it, swipe left from the
  right edge for the session details panel.
- **Full chat experience** — streamed turns with reasoning disclosure,
  markdown, terminal/diff/read/search/web tool cards, queue dock
  (edit / remove / steer), history paging, image attachments.
- **Everything the GUI does** — goals (phases, rounds, pause/resume/edit),
  plan mode + plan review, permission approvals, user questions, todo dock,
  subagents (catalog, follow-ups, interrupt), background jobs, workflow
  runs, skills, model selection, agent presets, session search, trajectory
  ledger, session export, message feedback.
- **Notifications** — turn complete, goal complete / blocked, review or
  question waiting for you; background connection via a foreground service.
- **Looks like the harness** — the exact DeepSeek Harness design tokens
  (colors, type, radii, disclosure rows, shimmer, ink buttons) with
  light / dark / system themes.
- **11 languages** — English, 中文, हिन्दी, Español, Français, العربية,
  বাংলা, Português, Русский, اردو, ไทย (RTL aware).

## Requirements

- Android 8.0+ (minSdk 26).
- A running [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)
  (tested against `0.1.0-rc.5`).

## Quick start

1. Install the latest APK from
   [Releases](https://github.com/LabTeto/dsh-mobile/releases).
2. On your computer, make the harness reachable from your phone:
   - **USB / emulator:** `dsh web`, then
     `adb reverse tcp:3080 tcp:3080` — in the app connect to
     `127.0.0.1:3080`.
   - **Wi-Fi:** apply the one-file LAN patch described in
     [`harness/README.md`](harness/README.md), restart `dsh web`, then tap
     **Scan network** in the app.
3. Pick a session, chat, and get notified when the harness is done.

## Compatibility & security

- See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) for the harness version
  matrix and loopback-only surfaces.
- **Read [docs/SECURITY.md](docs/SECURITY.md) first** — the harness has no
  authentication; only use LAN mode on trusted networks.

## Building

```sh
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK (signed when keystore env is set)
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development loop against a
real harness, the module layout, and the release workflow.

## Repository

| Path | What |
|---|---|
| `core/` | Pure-JVM protocol core: wire DTOs, RPC client, WebSocket downlinks, reconnect loop, session folding, notification classifier |
| `app/` | Android UI: screens, discovery/connection, foreground service, notifications, i18n |
| `mock-harness/` | Ktor mock of the harness `/api` server for tests |
| `tools/capture/` | Records real harness traffic into conformance fixtures |
| `harness/` | Companion patch + guide for LAN mode |

## License

[MIT](LICENSE). The DeepSeek Harness and its brand are property of their
respective owners; this project is an independent, community-built remote.
