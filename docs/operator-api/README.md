# ClashFest Operator API

A spec for VPN operators to customise the ClashFest Android client they ship
to their users — branding, support links, user-context, default behaviour —
all delivered through HTTP response headers on the subscription URL.

> Status: **draft (v1 in design).** This document is the source of truth
> while we design and implement. Implementation status per-header is tracked
> in [headers.md](headers.md) (`v1` / `v2` / `v3` / `proposed`).

## What this is

Every time the ClashFest client fetches your subscription URL it reads a set
of HTTP response headers that describe:

- **Brand identity** — name, logo (dark + light variants), tagline, accent color
- **Operator info** — website, support / telegram / bot, privacy / terms / help, status, renew
- **Per-user context** — already covered by `profile-title` (free-form, panel-controlled)
- **UX simplification** — hide Stats / Logs / Routing tabs for end users
- **Subscription policy** — share-link policy, HWID enforcement, max devices
- **Announcements** — broadcast messages with optional URLs

The client treats all of this as **operator hints** — it never trusts them
blindly. Each value goes through validation (max length, format, URL
whitelist, image size limits, SSRF protection). See [security.md](security.md).

## Why headers and not a JSON endpoint?

- Works with the existing subscription URL — no extra endpoints, no auth state
- Every Clash-compatible panel (Pasarguard / Marzban / Marzneshin / Remnawave /
  3x-ui / X-UI) already supports custom response headers on subscription
  routes — your panel team adds the values, the client reads them
- Trivially proxiable / cacheable
- Forward-compatible: clients that don't understand a header just ignore it

## Header naming

All ClashFest-specific headers use the `X-Brand-*` prefix.

Some headers are kept under their conventional V2Ray / Clash-compatible names
(`profile-title`, `Subscription-Userinfo`, `announce`, `share-links`,
`x-hwid-*`) because they're already widely supported by panels. We extend
those rather than rename them.

Headers are matched **case-insensitively**.

## Quick example

A subscription response might look like:

```
HTTP/1.1 200 OK
Content-Type: application/x-clash
Subscription-Userinfo: upload=1234; download=5678; total=107374182400; expire=1735689600

profile-title: vasya@example.com — Premium

X-Brand-Name: SwiftVPN
X-Brand-Tagline: Fast and private since 2024
X-Brand-Logo-URL: https://swiftvpn.example.com/static/logo-dark-256.png
X-Brand-Logo-Light-URL: https://swiftvpn.example.com/static/logo-light-256.png
X-Brand-Accent-Color: #5E35B1

X-Brand-Website-URL: https://swiftvpn.example.com
X-Brand-Support-URL: https://t.me/swiftvpn_support
X-Brand-Telegram-URL: https://t.me/swiftvpn_news
X-Brand-Bot-URL: https://t.me/swiftvpn_bot
X-Brand-Privacy-URL: https://swiftvpn.example.com/privacy
X-Brand-Terms-URL: https://swiftvpn.example.com/terms
X-Brand-Help-URL: https://swiftvpn.example.com/help
X-Brand-Renew-URL: https://swiftvpn.example.com/billing

announce: New servers added in Frankfurt and Amsterdam.
announce-url: https://swiftvpn.example.com/news/2026-05
```

After ClashFest reads this:

- Main screen header says **SwiftVPN** with the operator logo to the left,
  no longer "ClashFest" — the light/dark logo variant matches the user's
  current theme
- Primary accent color across the UI is `#5E35B1` (assuming it passes the
  contrast filter — see [security.md](security.md))
- About screen shows "SwiftVPN — powered by ClashFest" with links to
  Website / Privacy / Terms / Help / Telegram / Bot
- Profile card shows the subscription name "vasya@example.com — Premium"
  (operator-controlled free-form title)
- The announcement bar shows the operator message and links to the news page
- When the subscription is <3 days from expiry, the existing critical-expiry
  chip becomes tappable → opens the Renew URL; a "Renew" entry also appears
  in the profile overflow menu

## Documents

- [**headers.md**](headers.md) — full reference of every header, with type,
  example, semantics, validation rules, fallback, and implementation status.
- [**template-variables.md**](template-variables.md) — how panel
  template variables (`{{USERNAME}}`, `{{DAYS_LEFT}}`, etc.) flow into
  brand headers, with per-panel cheat-sheets and practical recipes.
- [**security.md**](security.md) — what the client validates, threat model,
  SSRF protection, image-size limits.
- [**branding-quickstart.md**](branding-quickstart.md) — step-by-step setup
  on Pasarguard / Marzban / Remnawave / 3x-ui.

## For panel developers

If you maintain a panel that wants first-class ClashFest brand support, the
fastest path is:

1. Read [headers.md](headers.md) and pick the subset you want to expose
   (most operators start with `X-Brand-Name` + `X-Brand-Logo-URL` +
   `X-Brand-Accent-Color`)
2. Add UI in your admin panel for the operator to configure those values
3. Echo them back on every subscription HTTP response

Most modern panels already support a "custom headers" feature — operators
can paste the headers as a list without panel changes.

## License

This spec is intentionally permissive — implement it, mirror it, fork it,
extend it. If you build something on top, a link back to this repo is
appreciated but not required.
