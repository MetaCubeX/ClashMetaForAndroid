# Security Smoke Checklist (Pre-Tag)

Run this checklist before cutting a release tag.

## Runtime hardening

- Start VPN in `Rule` mode and verify tunnel stays up for at least 2-3 minutes.
- Open `Override` settings and confirm no externally exposed controller endpoint is required for normal use.
- Verify local listeners remain loopback-only under session overrides.
- Verify no static auth/secret values are written to profile files after restart.

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
