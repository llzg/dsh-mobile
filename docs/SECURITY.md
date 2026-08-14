# Security

DSH Mobile is a remote control for the **DeepSeek Harness**. Understand the
trust model before using it.

## The harness has no authentication

The harness web server (`dsh web`) serves plain HTTP with a *trust fence*,
not authentication:

- Every `/api` request is accepted only when its `Host` header is loopback or
  a configured trusted authority (LAN IP literals are auto-derived when the
  server binds `0.0.0.0`).
- There are no tokens, cookies, or TLS. Any device on the same network can
  send requests with a trusted `Host` and drive the agent — including
  running commands on the host computer.

**Consequences:**

- Only bind the harness to `0.0.0.0` on networks you fully trust (home
  network, your own lab). Never on public or guest Wi-Fi.
- DSH Mobile shows a warning banner whenever you connect to a non-loopback
  host.
- Sensitive surfaces (settings, credentials, agent-preset authoring, host
  file pickers) remain loopback-only by harness design and are shown
  read-only over the network.

## What DSH Mobile stores

- Remembered host addresses (host, port, display name) and app preferences,
  in app-private storage only.
- No session content is persisted to disk in v1 (chat history lives in
  memory and is re-fetched on connect).
- The app allows cleartext HTTP by necessity (the harness serves plain
  HTTP); see `app/src/main/res/xml/network_security_config.xml`. All
  connections are user-initiated LAN endpoints.

## Reporting a vulnerability

Please report security issues privately to the repository maintainers
(see the repo's Security tab / maintainer contact in README). Do not open a
public issue for a vulnerability.

## Roadmap

Upstream harness improvements that would materially harden this setup are
tracked as issues in this repository:

1. An authentication layer (pairing token) on the web server.
2. An explicit `--lan` flag (currently the CLI blocks `--host 0.0.0.0`).
3. mDNS advertisement for zero-touch, authenticated discovery.
