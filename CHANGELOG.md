# Changelog

All notable changes to DSH Mobile are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/); the project uses SemVer.

## [0.1.0] - unreleased

Initial release.

### Added

- Connection to a DeepSeek Harness (v0.1.0-rc.5) over the web `/api` protocol
  (HTTP unary + dual WebSocket event streams, reconnect with backoff).
- Discovery: manual host entry, active Wi-Fi subnet scan, remembered hosts,
  loopback (same-device) connection, auto-connect toggles.
- Discord-style navigation: swipe from the left edge opens the workspace-
  grouped chat list; right-edge swipe opens the session details panel.
- Chat: streamed turns, reasoning disclosure, markdown, tool cards
  (terminal/diff/read/search/web/generic), queue dock (edit/remove/steer),
  history paging, image attachments.
- Feature modules: goals, plan mode + plan review, approvals, user
  questions, todo dock, subagents, background jobs, workflow runs, skills,
  model selection, agent presets, settings (read-only over LAN), trajectory
  ledger, session export, message feedback.
- Notifications: turn complete, goal complete/blocked, review/question
  requested; foreground service for background connection.
- DeepSeek Harness visual design system (colors, typography, radii,
  components) with light/dark/system themes.
- Localization: en, zh-Hans, hi, es, fr, ar, bn, pt, ru, ur, th (RTL aware).
- Harness-side LAN companion (`harness/`) and developer tooling
  (`mock-harness/`, `tools/capture/`).
