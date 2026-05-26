# Security Smoke Checklist (Pre-Tag)

Run this checklist before cutting a release tag.

## Runtime hardening

- Start VPN in `Rule` mode and verify tunnel stays up for at least 2-3 minutes.
- Open `Override` settings and confirm no externally exposed controller endpoint is required for normal use.
- Verify local listeners remain loopback-only under session overrides.
- Verify no static auth/secret values are written to profile files after restart.

## Imported YAML hardening (`YamlHardener`)

Import a profile whose `config.yaml` contains a hostile-looking surface — e.g.:

```yaml
allow-lan: true
external-controller: 0.0.0.0:9090
bind-address: '*'
listeners:
  - name: leaky-socks
    type: socks
    listen: 0.0.0.0
    port: 1080
```

After the import completes, open the on-disk `config.yaml` under
`<filesDir>/clash/profiles/<uuid>/config.yaml` and confirm:

- `listeners:` block is **absent** when hardening mode is `Strict` (default on Android 10+).
- `listeners:` block keeps every entry with `listen: 127.0.0.1` when hardening mode is `Compat`.
- `allow-lan` is `false` regardless of mode (when mode != `Off`).
- `bind-address` is `127.0.0.1`.
- `external-controller` host is `127.0.0.1` (port preserved).
- A `config.original.yaml` sibling file exists with the **unmodified** subscription YAML.

Trigger a subscription refresh and re-check: hardening must be re-applied (idempotent),
and the user's original snapshot in `config.original.yaml` must remain untouched
from the first import. A custom user-edited listener bound to `127.0.0.1` with
auth must survive the refresh (see `SubscriptionUpdateMerge.listeners` preservation).

## Functional regression

- Start/stop VPN five times in a row (no crash, no stuck "starting" state).
- Switch profile while VPN is running (traffic resumes, mode remains correct).
- Toggle split-tunnel/access-control modes and verify traffic still routes as expected.
- Trigger subscription update and verify runtime remains alive even if one update fails.
- Reconnect sequence: stop VPN -> start VPN -> switch mode (`Rule/Global/Direct`) -> run traffic.

## Logging and privacy

- In release build, confirm verbose/info logs are suppressed.
- Confirm no logs contain secrets, auth credentials, full controller endpoints, or DNS dump values.

## CI/Policy checks

- CI guard passes: no `HandlerService` reference in source tree.
- Prerelease workflow updates only `dev-latest` assets and keeps changelog range correct.
