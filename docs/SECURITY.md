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
  HTTP); see `app/src/main/res/xml/network_security_config.xml`.

## What DSH Mobile connects to

Every connection is to a LAN endpoint you entered or picked from a scan, with
one exception:

- **The update check.** On start the app asks
  `api.github.com` for this repository's latest release, over HTTPS, so it can
  tell you when a newer APK exists. It sends no identifying information beyond
  what any HTTPS request carries, and it is the only request that leaves your
  network. Turn it off in **Settings → About → Check for updates**; nothing else
  in the app contacts anything but the harness.
- **Scanning** probes only your own device's IPv4 /24, and only with a TCP
  connect followed by the harness's own `host.describe`.

## Reporting a vulnerability

Please report security issues privately to **sor@zyphite.com**. Do not open a
public issue for a vulnerability. Include what an attacker can do, the steps to
reproduce it, the app and harness versions, and how the app was connected.

The harness having no authentication is the documented model above, not a
vulnerability report worth sending. A way around the trust fence is.

## Roadmap

Upstream harness improvements that would materially harden this setup are
tracked as issues in this repository:

1. An authentication layer (pairing token) on the web server.
2. An explicit `--lan` flag (currently the CLI blocks `--host 0.0.0.0`).
3. mDNS advertisement for zero-touch, authenticated discovery.
