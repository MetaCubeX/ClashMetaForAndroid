# Subscription HWID headers and operator policies

## HTTP headers (client → subscription server)

All subscription-related HTTP requests made by ClashFest include device fingerprint headers (stable, non-secret SHA-256 id):

| Header | Meaning |
|--------|---------|
| `x-hwid` | SHA-256 of `cf|packageName|ANDROID_ID` (hex) |
| `x-device-os` | `Android` |
| `x-ver-os` | Android release (e.g. `15`) |
| `x-device-model` | Manufacturer + model |
| `x-app-version` | App `versionName` |

These are attached to:

- Native profile/provider fetch (`fetchAndValid` / Go `openUrl`)
- OkHttp probes in `ProfileProcessor` / `ProfileManager`
- Kotlin metadata probe (`SubscriptionMetadataFetcher`, `SubscriptionNameGuesser`)

## Share-link lock (`share-links` response header)

If the subscription HTTP response includes any of:

- `share-links`, `Share-Links`, `share_links`, `X-Share-Links`, `X-Share-Links-Policy`

with value `true` / `1` / `yes` / `on`, the app:

1. Sets `ServiceStore.setSubscriptionShareLinksLockedFor(<profile uuid>, true)` — **per-profile**, not global
2. Hides **Share subscription link** on that profile's overflow menu
3. Disables editing the subscription **URL** in quick edit and full properties for that profile (name/interval still editable)

Value `false` / `0` / `no` / `off` clears the lock on the next successful metadata sync. The lock entry is purged on profile delete.

Different subscriptions on the same device keep independent share policies.

## Cleartext metadata probe (opt-in)

By default, subscription **metadata** probes skip `http://` URLs. Users can enable **Allow HTTP for subscription metadata probe** under Announcement & Support settings.

## Operator API headers (X-Brand-*)

A full operator-customisation surface lives under the `X-Brand-*` namespace
in the same subscription HTTP response. See [`docs/operator-api/`](operator-api/)
for the complete spec.

### Quick-reference: every supported header

| Header | Purpose | Notes |
|---|---|---|
| `X-Brand-Name` | App title in main header and About | string, max 32 |
| `X-Brand-Tagline` | One-line subtitle under the brand name | string, max 64 |
| `X-Brand-Logo-URL` | Primary (dark-theme) logo | HTTPS only, PNG/WebP/JPEG, ≤512KB, no SVG |
| `X-Brand-Logo-Light-URL` | Light-theme variant of the logo | optional, same constraints |
| `X-Brand-Accent-Color` | Material 3 seed for the harmonised palette | `#RRGGBB`; WCAG-contrast filtered |
| `X-Brand-Website-URL` | Operator website | URL |
| `X-Brand-Support-URL` | Operator support contact | URL — **legacy** `support-url` is NOT auto-promoted here, it lives in the announcement card path instead |
| `X-Brand-Telegram-URL` | Telegram channel | URL |
| `X-Brand-Bot-URL` | Telegram bot | URL |
| `X-Brand-Privacy-URL` | Privacy policy | URL |
| `X-Brand-Terms-URL` | Terms of service | URL |
| `X-Brand-Help-URL` | FAQ / help centre | URL |
| `X-Brand-Status-URL` | Service-status page | URL |
| `X-Brand-Renew-URL` | Deep link for subscription renewal | URL; surfaces on critical-expiry chip + Renew button on Operator tab |
| `X-Brand-Show-Operator-Tab` | Explicit opt-in for the dedicated Operator tab | boolean. Without this, brand is visual-only — name / logo / accent still apply, but no tab appears |
| `X-Brand-Hide-Routing` | When paired with `Show-Operator-Tab=true`, the Operator tab replaces Routing | boolean; alone, no-op |

### Activation rules

- **Brand identity active** = at least one of `X-Brand-Name`, `X-Brand-Logo-URL`,
  `X-Brand-Logo-Light-URL`, `X-Brand-Accent-Color` is present. Operator-info
  URLs alone (only support / privacy / help) do NOT activate brand UI.
- **Operator tab appears** = brand identity is active **AND**
  `X-Brand-Show-Operator-Tab: true`. Explicit opt-in.
- **Routing replaced** = `X-Brand-Show-Operator-Tab: true` **AND**
  `X-Brand-Hide-Routing: true`.

### Storage scope

All brand state is stored **per source profile UUID**. Switching the active
profile flips the visible brand instantly without re-fetching the subscription;
deleting a profile purges its brand entry and any cached logo files.
