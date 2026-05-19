# Supported HTTP headers (operator / client)

One-stop reference for every HTTP header ClashFest **sends** to or **reads
from** a subscription URL. Useful as the single page to bookmark / read on
GitHub when you're integrating a panel.

Living spec — kept in sync with code under `common/branding/`, `service/
branding/`, and `common/util/SubscriptionMetadata.kt`.

---

## 1. What ClashFest sends (request → subscription server)

Device-binding fingerprint headers attached to every subscription / provider
fetch. Stable SHA-256 id, non-secret.

| Header | Meaning |
|---|---|
| `x-hwid` | SHA-256 of `cf\|packageName\|ANDROID_ID` (hex) |
| `x-device-os` | `Android` |
| `x-ver-os` | Android release (e.g. `15`) |
| `x-device-model` | Manufacturer + model |
| `x-app-version` | App `versionName` |
| `User-Agent` | Configurable per profile via "User-Agent override" |

Attached by: native fetch (Go `openUrl`), OkHttp probes in
`ProfileProcessor` / `ProfileManager.updateFlow`, Kotlin metadata probe
(`SubscriptionMetadataFetcher`), name guesser
(`SubscriptionNameGuesser`).

---

## 2. What ClashFest reads (server → client response)

### 2.1 Subscription metadata (legacy / V2Ray-compatible)

Already understood by every existing Clash panel. Surface through the
home announcement card / profile card / chips. No `X-Brand-*` prefix
because these existed before our operator API.

| Header | Type | Meaning |
|---|---|---|
| `Subscription-Userinfo` | `upload=N; download=N; total=N; expire=UNIX` | Quota progress + critical-expiry chip |
| `profile-title` / `Profile-Title` / `Subscription-Title` / `X-Subscription-Title` / `Display-Name` | string (UTF-8 or `base64:`) | Subscription / user name on the profile card |
| `profile-update-interval` / `Update-Interval` / `X-Profile-Update-Interval` | integer (hours, 1–720) | Auto-update interval. Coerced to ≥15 min. |
| `profile-web-page-url` / `Subscription-Web-Page` | URL | Cached; operator-personal-cabinet link |
| `support-url` / `Profile-Support-URL` / `Subscription-Support-URL` / `Support` | URL | Surfaces as the support button in the announcement card. **Does NOT activate brand UI on its own.** |
| `announce` / `Announce` / `Announcement` / `X-Announcement` | string (UTF-8 or `base64:`) | Operator broadcast — home announcement card body |
| `announce-url` / `Announcement-URL` / `X-Announcement-URL` | URL | Tap target for the announcement bar |
| `share-links` / `X-Share-Links` / `X-Share-Links-Policy` | boolean | **Per-subscription:** hides Share/Copy-link actions and locks URL editing for that profile |
| `x-hwid-active` | boolean | Panel HWID acceptance — shown in HWID diagnostics |
| `x-hwid-not-supported` | boolean | Panel HWID feature unsupported |
| `x-hwid-max-devices-reached` | boolean | Panel device-cap reached |
| `x-hwid-limit` | boolean | Panel general limit hit |

Notes on `share-links`:
- Stored per profile UUID (`subscription_share_links_locked_<uuid>`),
  not globally — different operators can coexist with different policies.
- `false` / `0` / `no` / `off` clears the lock on the next metadata sync.
- The lock entry is purged when the profile is deleted.

### 2.2 Operator API — `X-Brand-*` namespace

ClashFest-specific. Use the `X-Brand-*` prefix in your panel's
custom-headers config. Full spec with security rules and storage details
lives in [`docs/operator-api/`](operator-api/).

#### Master switch — required to opt in

| Header | Type | Notes |
|---|---|---|
| `X-Branding-Enabled` | boolean | **Required.** Branding only applies when this is `true`. Absent / `false` / `null` → every `X-Brand-*` field is ignored and the UI stays default. This makes branding an explicit per-subscription toggle the operator must turn on, not something one stray field accidentally activates. |

#### Per-user context (template-variable driven)

| Header | Type | Notes |
|---|---|---|
| `X-Brand-User-Display-Name` | string ≤64 | Surfaces as "Logged in as X" in About. Typically wired via `{{USERNAME}}` template — see [template-variables.md](operator-api/template-variables.md). |
| `X-Brand-Greeting` | string ≤120 | Hero line on the Operator tab. Free-form, ideal for `{{USERNAME}}` + `{{DAYS_LEFT}}` style greeting. Falls back to "Hello, <display-name>!" if absent. |

#### Brand identity

| Header | Type | Notes |
|---|---|---|
| `X-Brand-Name` | string | App title in main header / About. ≤32 chars. |
| `X-Brand-Tagline` | string | Subtitle under brand name. ≤64 chars. |
| `X-Brand-Logo-URL` | URL | Primary (dark-theme) logo. **HTTPS only.** PNG / WebP / JPEG. ≤512KB. SSRF-guarded. |
| `X-Brand-Logo-Light-URL` | URL | Optional light-theme variant. Falls back to primary on dark theme. |
| `X-Brand-Accent-Color` | `#RRGGBB` | Material 3 seed; full tonal palette derived via harmonisation. WCAG-contrast filtered. |

#### Operator info / external links

All URL fields accept `https://`, `tg://`, `mailto:`, `t.me/`
(auto-promoted). Anything else is ignored.

| Header | Notes |
|---|---|
| `X-Brand-Website-URL` | About → Website chip |
| `X-Brand-Support-URL` | Operator support contact. Replaces the legacy `support-url` source when present. |
| `X-Brand-Telegram-URL` | Telegram channel |
| `X-Brand-Bot-URL` | Telegram bot |
| `X-Brand-Privacy-URL` | About → Privacy chip |
| `X-Brand-Terms-URL` | About → Terms chip |
| `X-Brand-Help-URL` | About → Help chip |
| `X-Brand-Status-URL` | Service-status page link |
| `X-Brand-Renew-URL` | Deep link for renewal. Critical-expiry chip becomes tappable when set; Renew CTA appears on the Operator tab. |
| `X-Brand-Cabinet-URL` | Per-user personal account / billing deep link built with a panel template variable (e.g. `https://t.me/<bot>?startapp={{SHORT_UUID}}` for a Telegram Mini App, or `https://billing.example.com/account?ref={{ID}}`). Surfaces as the "My account" button on the Operator tab. |

#### UX surfaces

| Header | Type | Notes |
|---|---|---|
| `X-Brand-Show-Operator-Tab` | boolean | **Explicit opt-in** for the dedicated Operator tab. Visual brand (name / logo / accent) applies without this; the tab does not. |
| `X-Brand-Hide-Routing` | boolean | Paired with `Show-Operator-Tab=true`, Operator **replaces** Routing in the bottom nav. No-op on its own. |
| `X-Brand-Hide-Stats` | boolean | (v3, UI deferred) Hide Network Stats screen entry |
| `X-Brand-Hide-Logs` | boolean | (v3, UI deferred) Hide Logs entry in Settings |

---

## 3. Activation rules

**Brand identity active** = at least one of `X-Brand-Name` /
`X-Brand-Logo-URL` / `X-Brand-Logo-Light-URL` / `X-Brand-Accent-Color`
is present.

Operator-info URLs alone (e.g. only `X-Brand-Support-URL` or only the
legacy `support-url`) do **not** activate brand UI. They flow through
the announcement card path instead.

**Operator tab appears** = brand identity active **AND**
`X-Brand-Show-Operator-Tab: true`.

**Routing replaced by Operator** = both `X-Brand-Show-Operator-Tab: true`
**AND** `X-Brand-Hide-Routing: true`.

---

## 4. Storage scope

| Concern | Scope | Lifecycle |
|---|---|---|
| `Subscription-Userinfo` quota | Per profile | Updated each sync |
| `profile-title` | Per profile (Imported row) | Updated each sync |
| `share-links` lock | Per profile UUID | Cleared on profile delete |
| `x-hwid-*` flags | Latest only (single set) | UiStore, last-seen value |
| `announce` / `announce-url` / `support-url` | Cached for active profile | Updated each sync |
| All `X-Brand-*` (manifest + cached logo paths) | Per profile UUID | Cleared on profile delete |
| `lastAppliedAccent` (Activity recreate trigger) | Global | Persists across resets |

Cached logo files live under `<app filesDir>/brand/<sha256(url)>.bin`.
Orphan files (no profile still references them) are pruned after every
brand apply / delete.

---

## 5. Validation summary

Briefly — full rules in [`docs/operator-api/security.md`](operator-api/security.md):

- **Strings** trimmed, control chars stripped, `base64:` prefix decoded,
  truncated to per-field max length.
- **URLs** require `https://` / `tg://` / `mailto:` / `t.me/` schemes
  (operator-info fields). Logo URLs require `https://` only.
- **Hex colors** `#RRGGBB` exact regex; WCAG luminance contrast against
  the surface ≥3:1 (AA large-text minimum), otherwise ignored.
- **Booleans** accept `true`/`1`/`yes`/`on` / `false`/`0`/`no`/`off`,
  case-insensitive. Unknown → treated as absent.
- **Logo download** HTTPS-only, SSRF-guarded against private IP redirects
  (max 3 hops), Content-Type whitelist (`image/png|webp|jpeg`), 512KB cap,
  decompression-bomb guard rejects images that would exceed 4MB
  ARGB-8888 after decode.

---

## 6. Identity-cannot-silently-change invariant

By design, branding never changes the app's underlying identity:

- The Android launcher icon, package name, and app-store listing are
  fixed at build time and immune to header changes.
- The About sheet always shows `powered by ClashFest` (untranslatable)
  alongside the operator name.
- All branding values are validated, capped, and revert when the
  operator stops sending them — no permanent lock-in from a hostile
  subscription.

---

## 7. Quick test recipe

Add these headers to your panel's subscription response (or via an
nginx / Caddy reverse-proxy if your panel doesn't expose custom headers
natively — see [`branding-quickstart.md`](operator-api/branding-quickstart.md)):

```
X-Brand-Name: SwiftVPN
X-Brand-Tagline: Fast and private
X-Brand-Logo-URL: https://cdn.example.com/logo-dark.png
X-Brand-Accent-Color: #5E35B1
X-Brand-Website-URL: https://swiftvpn.example.com
X-Brand-Support-URL: https://t.me/swiftvpn_support
X-Brand-Privacy-URL: https://swiftvpn.example.com/privacy
X-Brand-Terms-URL: https://swiftvpn.example.com/terms
X-Brand-Renew-URL: https://swiftvpn.example.com/billing
X-Brand-Show-Operator-Tab: true
```

After force-updating the profile in the app:
- Main header shows `SwiftVPN` + small logo
- About sheet has operator-info chips + Renew button
- A new "Operator" tab appears in the bottom nav with all operator links
- Power-button accent in the running state becomes `#5E35B1`-derived
- Settings switches / chips / progress bars use the harmonised palette

---

## Related

- [`docs/operator-api/`](operator-api/) — full Operator API spec
- [`docs/subscription-hwid-and-share-policy.md`](subscription-hwid-and-share-policy.md) — deeper notes on HWID + share-links policies
