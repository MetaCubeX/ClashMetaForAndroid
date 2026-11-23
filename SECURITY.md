# Security Policy

## Reporting a vulnerability

Report security issues privately. Do not open a public issue or include exploit details in a pull
request.

Contact Clarence Henry through the Telegram channel listed in the app and [README.md](README.md),
or through the Coolapk profile linked there. Include the affected version, a description of the
impact, and reproduction steps. Avoid attaching real subscription URLs, WebDAV credentials, or
device identifiers.

## Scope

In scope: the Android app, the service and design modules, the Go bridge, build and release
workflows, and the local MCP server.

Out of scope: vulnerabilities in mihomo itself, in Android, or in third-party servers you configure
the app to use. Report those to the respective upstream projects.

## Secrets

`release.keystore` is the same file upstream publishes and stays in the repository. The repository
must not contain keystore passwords, API tokens, real WebDAV endpoints, or device serial numbers.
If you find one, report it privately instead of opening an issue.
