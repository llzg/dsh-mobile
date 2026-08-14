# LAN mode for the DeepSeek Harness (companion setup for DSH Mobile)

The DeepSeek Harness web server binds to `127.0.0.1` by default, and the
`dsh web --host 0.0.0.0` flag is intentionally blocked for safety (the
harness has no authentication layer yet). To use DSH Mobile over Wi-Fi you
enable LAN serving through the harness **user patch layer** — the supported
configuration seam.

## Steps

1. Locate your harness home: `$DSH_HOME` or `~/.dsh` (on Windows typically
   `C:\Users\<you>\.dsh`). The web profile lives at
   `<harness-home>/profiles/web/cordis.patch.yml`.

2. If the file does not exist, create it. Append (or merge) the row from
   [`cordis.patch.lan.yml`](./cordis.patch.lan.yml) — it restates the
   `webserver` row to bind all interfaces:

   ```yaml
   - id: webserver
     name: '@deepseek-ai/dsh-host-webserver'
     inject: [webStartup]
     config:
       host: '0.0.0.0'
       port: 3080
   ```

3. Restart the harness web profile:

   ```sh
   dsh web
   ```

   The URL line now prints a LAN address:

   ```
   dsh web: http://127.0.0.1:3080 (LAN: http://192.168.1.20:3080)
   ```

   When bound to all interfaces, the harness automatically trusts its own
   LAN IP literals (the `/api` trust fence derives them from the bind host),
   so no `--trusted-host` entry is needed for a plain IP connection. If you
   reach the harness through a hostname instead, add it explicitly:

   ```sh
   dsh web --trusted-host myhost.local
   ```

4. In DSH Mobile: Settings → Connect, tap **Scan network**, or enter
   `192.168.1.20` / `3080` manually.

## Notes

- **Security**: there is no authentication. Anyone on your LAN can reach the
  harness while it binds `0.0.0.0`. Only use LAN mode on networks you trust.
  See [../docs/SECURITY.md](../docs/SECURITY.md).
- **Privileged features**: settings, credentials, host directory pickers and
  agent-preset authoring stay loopback-only by design; the app shows them
  read-only with a banner when connected over the network.
- **Revert**: delete the patch row and restart to return to loopback-only.
- **Same device**: to drive a harness running on the phone itself (e.g. via
  Termux) or via `adb reverse tcp:3080 tcp:3080`, just connect to
  `127.0.0.1:3080` — no patch needed.
