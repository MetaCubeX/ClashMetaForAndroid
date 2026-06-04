# ClashFest Operator API — header reference

Status legend:

- **v1** — implemented now (or planned for the current branch)
- **v2** — planned next wave (extended operator info + policy display)
- **v3** — later (operator-controlled UI simplification)
- **proposed** — accepted into the spec, not yet scheduled

All headers are matched case-insensitively. Empty / blank values are treated
as "header not present". String fields may be plain UTF-8 or `base64:`-prefixed
(`X-Brand-Name: base64:U3dpZnRWUE4=`) — the client decodes both.

ClashFest's primary theme is dark. Wherever a "light" alternative exists,
it's the optional override.

---

## 0. Master switch

### `X-Branding-Enabled`

| | |
|---|---|
| Type | boolean |
| Status | **v1** |
| Required for | **Any branding to apply.** Without this header set to `true`, every other `X-Brand-*` field is ignored and the client shows the default ClashFest UI. |
| Default | absent / `false` / `null` → **branding off** |
| Notes | Branding is **explicit opt-in per subscription**. Setting `X-Brand-Name`, `X-Brand-Logo-URL`, etc. without also sending `X-Branding-Enabled: true` is a no-op — the headers are parsed and persisted, but the UI stays default. To roll back a misconfigured deployment, drop this header (or set `false`); brand state on the client reverts immediately after the next subscription refresh. |

**Example to enable branding:**
```
X-Branding-Enabled: true
X-Brand-Name: SwiftVPN
X-Brand-Logo-URL: https://cdn.example.com/logo-dark.png
X-Brand-Accent-Color: #5E35B1
```

**Example to disable / kill switch:**
```
X-Branding-Enabled: false
```

---

## 1. Brand identity

### `X-Brand-Name`

| | |
|---|---|
| Type | string |
| Max length | 32 characters |
| Status | **v1** |
| Applied to | Main screen header title (replaces "ClashFest"); About screen |
| Fallback | "ClashFest" |
| Validation | trimmed; control characters stripped; blank → ignored |

**Example:**
```
X-Brand-Name: SwiftVPN
```

### `X-Brand-Tagline`

| | |
|---|---|
| Type | string |
| Max length | 64 characters |
| Status | **v1** |
| Applied to | About screen subtitle; optional small subtitle under the brand name in the main header |
| Fallback | empty (no tagline shown) |

**Example:**
```
X-Brand-Tagline: Fast and private since 2024
```

### `X-Brand-Logo-URL`

| | |
|---|---|
| Type | URL (https only) |
| Status | **v1** |
| Applied to | Round logo tile in main header (left of brand name); About screen icon |
| Image formats | PNG, WebP, JPEG. **No SVG** (see security.md) |
| Recommended size | 256×256, ≤200KB |
| Max size enforced | 512KB hard cap |
| Cache | disk at `<filesDir>/brand/<sha256(url)>`, atomic write |
| Validation | https-only, content-type whitelist, SSRF guard (no private IPs / redirects to private IPs), size cap |
| Fallback | app launcher icon |
| Notes | This is the **primary** logo, used by default. It's expected to look right on a dark background since dark is the app's default theme. |

**Example:**
```
X-Brand-Logo-URL: https://swiftvpn.example.com/static/logo-256.png
```

### `X-Brand-Logo-Light-URL`

| | |
|---|---|
| Type | URL (https only) |
| Status | **v1** |
| Applied to | Same as `X-Brand-Logo-URL` but used when the user is on the light theme |
| Validation | same as `X-Brand-Logo-URL` |
| Fallback | `X-Brand-Logo-URL` (so a dark-tuned logo is still used on light theme if the operator didn't ship a light variant) |
| Notes | Optional. Most operators only need to ship one logo. |

### `X-Brand-Accent-Color`

| | |
|---|---|
| Type | hex color `#RRGGBB` |
| Status | **v1** |
| Applied to | Global `colorPrimary` runtime override — affects power button, toggles, switches, progress bars, filled chips, selected states, accent surfaces across the app |
| Validation | regex `^#[0-9A-Fa-f]{6}$`; rejected if luminance contrast with surface is < 3:1 (WCAG AA minimum for large text) |
| Fallback | built-in theme accent |
| Notes | This is a "full white-label" knob. Pick a color that works in both the dark and the light theme (the contrast filter will reject one-side-only colors). Recommended saturation 30–70% so accent stays visible without being garish. |

**Example:**
```
X-Brand-Accent-Color: #5E35B1
```

---

## 2. Operator info / external links

All URL fields share the same validation: must be `https://`, `tg://`,
`mailto:`, or `t.me/` (auto-promoted to `https://t.me/`). Anything else
is ignored.

### `X-Brand-Website-URL`

| | |
|---|---|
| Type | URL |
| Status | **v1** |
| Applied to | About screen → "Visit website" button |

### `X-Brand-Support-URL`

| | |
|---|---|
| Type | URL |
| Status | **v1** (extension of existing `support-url` parsing) |
| Applied to | Support icon in profile card and bottom sheet; About screen |

> Backwards compatibility: also accepts `support-url`, `Profile-Support-URL`,
> and `Subscription-Support-URL`. If both `X-Brand-Support-URL` and a legacy
> form are present, `X-Brand-Support-URL` wins.

### `X-Brand-Telegram-URL`

| | |
|---|---|
| Type | URL (https / tg / t.me) |
| Status | **v1** |
| Applied to | About screen → "Telegram channel" button |

### `X-Brand-Bot-URL`

| | |
|---|---|
| Type | URL (https / tg / t.me) |
| Status | **v1** |
| Applied to | About screen → "Telegram bot" button (separate from channel) |

### `X-Brand-Privacy-URL`

| | |
|---|---|
| Type | URL |
| Status | **v1** |
| Applied to | About screen → "Privacy policy" link |

### `X-Brand-Terms-URL`

| | |
|---|---|
| Type | URL |
| Status | **v1** |
| Applied to | About screen → "Terms of service" link |

### `X-Brand-Help-URL`

| | |
|---|---|
| Type | URL |
| Status | **v1** |
| Applied to | About screen → "FAQ" / "Help center" button |

### `X-Brand-Status-URL`

| | |
|---|---|
| Type | URL |
| Status | **v2** |
| Applied to | Connection-lost dialog / failed-fetch dialog → "Check service status" button |

### `X-Brand-Renew-URL`

| | |
|---|---|
| Type | URL |
| Status | **v2** |
| Applied to | When subscription expiry is critical (<3 days or already expired):<br>• tap on the existing critical-expiry chip on the profile card<br>• "Renew" button in the profile bottom sheet near the expiry info<br>• "Renew subscription" entry in the profile overflow menu<br>About screen also gets a Renew button when this URL is set. |
| Notes | All entry points appear only when this URL is provided. No URL → no Renew UI anywhere. |

### `X-Brand-Cabinet-URL`

| | |
|---|---|
| Type | URL (https / tg / mailto) — almost always built with a panel template variable |
| Status | **v3** |
| Applied to | "My account" button on the **Operator** tab, rendered as a tonal-secondary button under the Renew CTA (or alone if no Renew URL). |
| Notes | The operator builds a **per-user** URL using their panel's identifier template — e.g. `{{SHORT_UUID}}`, `{{ID}}`, `{{USERNAME}}`. Examples:<br>• Telegram Mini App: `https://t.me/<bot>?startapp={{SHORT_UUID}}` (requires the bot to be registered as a Mini App in BotFather).<br>• Web cabinet: `https://billing.example.com/account?ref={{ID}}`<br>• Bot chat with start payload: `https://t.me/<bot>?start={{SHORT_UUID}}` (operator must handle the payload in `/start` and surface the cabinet button).<br>Client opens the URL via `ACTION_VIEW` — Android routes `tg://` / `https://t.me/...` to Telegram (Mini App or chat depending on the URL form), other `https://` to the browser. |

---

## 3. User context

The client uses **only one** field for user-facing identity. Per-user
personalisation lives in `profile-title` — operators put whatever they
want there (`vasya@example.com — Premium`, `John (Trial, 2d left)`, etc).
Free-form, panel-controlled.

### `profile-title` (existing, non-Brand)

| | |
|---|---|
| Type | string |
| Status | **v1** (already parsed) |
| Applied to | Profile card title |
| Notes | Carries the subscription plan label. For per-user identity / greeting, see the new headers below — modern panels support template variables that substitute user data into headers at request time. |

### `X-Brand-User-Display-Name`

| | |
|---|---|
| Type | string |
| Max length | 64 characters |
| Status | **v2** |
| Applied to | "Logged in as <name>" line in About sheet (under the brand identity block) |
| Notes | Designed to be filled via a panel template variable, e.g. `X-Brand-User-Display-Name: {{USERNAME}}`. See [template-variables.md](template-variables.md) for the full list per panel. |

### `X-Brand-Greeting`

| | |
|---|---|
| Type | string |
| Max length | 120 characters |
| Status | **v2** |
| Applied to | Hero line on the Operator tab, between the brand-identity block and the Renew CTA |
| Notes | Free-form. Operators typically wire dynamic content here via panel templates, e.g. `X-Brand-Greeting: Welcome back, {{USERNAME}}! {{DAYS_LEFT}} days remaining`. If absent but `X-Brand-User-Display-Name` is set, the client falls back to a built-in "Hello, <name>!". |

---

## 4. UX defaults — operator-controlled simplification

These let an operator hide advanced sections of the app from their users.
None of them is enforcement — the user can always re-enable an advanced
section through deep settings, the operator just chooses what to surface
by default.

We deliberately do **not** ship operator-controlled `default-mode`,
`recommended-group`, `locale`, or `theme` headers — mihomo already
handles tunnel mode and group selection via the YAML config, and locale /
theme should follow user preference, not operator preference.

### `X-Brand-Show-Operator-Tab`

| | |
|---|---|
| Type | boolean |
| Status | **v1** |
| Applied to | Adds a dedicated "Operator" entry to the bottom navigation with logo + name + tagline + Renew CTA + operator-info link list. |
| Notes | Explicit opt-in. Sending brand identity (name / logo / accent) alone is enough to brand the visuals — it does NOT auto-add a tab. Operators that want the consolidated info page choose it consciously. Pair with `X-Brand-Hide-Routing` to replace Routing instead of adding a 5th tab. |

### `X-Brand-Hide-Routing`

| | |
|---|---|
| Type | boolean |
| Status | **v1** |
| Applied to | When paired with `X-Brand-Show-Operator-Tab=true`, the Operator tab **replaces** Routing in the bottom-nav slot (still 4 tabs, just different middle). Alone, this header has no effect — hiding Routing without something to replace it would just remove a section the user needs. |

---

## 5. Subscription policy

### `Subscription-Userinfo` (existing)

| | |
|---|---|
| Format | `upload=N; download=N; total=N; expire=UNIX` |
| Status | **v1** (already parsed) |
| Applied to | Quota progress on profile card, critical-expiry chip |

### `profile-update-interval` (existing)

| | |
|---|---|
| Type | integer (hours) |
| Status | **v1** (already parsed) |
| Applied to | Auto-update interval, coerced to ≥15 min |

### `share-links` (existing)

| | |
|---|---|
| Type | boolean (`true`/`1`/`yes`/`on` = disable sharing) |
| Status | **v1** (already parsed) |
| Applied to | Hides "Copy node link" / "Share" actions in the picker AND locks subscription URL editing for **that subscription only** |
| Notes | Stored per-profile (`subscriptionShareLinksLockedFor(uuid)`). Different subscriptions can have different share policies — one operator's lock does not affect another's subscription on the same device. |

### `x-hwid-active` / `x-hwid-not-supported` / `x-hwid-max-devices-reached` / `x-hwid-limit` (existing)

| | |
|---|---|
| Type | boolean |
| Status | **v1** (already parsed) |
| Applied to | HWID enforcement warnings on profile card |

---

## 6. Announcements

### `announce` / `Announcement` (existing)

| | |
|---|---|
| Type | string (UTF-8 or `base64:`) |
| Max length | 256 chars |
| Status | **v1** (already parsed) |
| Applied to | Inline announcement bar on profile card |

### `announce-url` / `Announcement-URL` (existing)

| | |
|---|---|
| Type | URL |
| Status | **v1** (already parsed) |
| Applied to | Tap target for the announcement bar |

---

## Implementation order

| Wave | Adds |
|---|---|
| **v1** | Section 1 (brand identity, all five) + section 2 entries (Website / Support / Telegram / Bot / Privacy / Terms / Help) + already-parsed sections 5 + 6 |
| **v2** | Section 2 v2 entries (Status-URL, Renew-URL) |
| **v3** | Section 4 (Hide-Routing) |
| proposed | Anything added by future PRs |

Anything not listed as a defined header is ignored. Operators are free to
send other custom headers — the client doesn't care.

## What we deliberately don't ship (and why)

Headers that were considered and rejected during the v1 design review:

- `X-Brand-User-Display-Name` / `User-Tier` / `User-Greeting` / `User-Flair` —
  per-user personalisation isn't possible on most panels through static
  response headers. The operator can already shape `profile-title`
  freely to carry user name, tier, and any other personal context.
- `X-Brand-Trial-Until` — covered by the existing `Subscription-Userinfo`
  `expire=` field plus the critical-expiry chip; no need for a parallel
  trial-specific channel.
- `X-Brand-Default-Mode` — mihomo already reads `mode:` from the YAML
  config, no need for a header equivalent.
- `X-Brand-Recommended-Group` — mihomo's Selector default already picks
  the first proxy in the configured list. Operators control this through
  the YAML, not headers.
- `X-Brand-Locale` / `X-Brand-Theme` — these are user preferences. Letting
  an operator override them silently is hostile UX, even when well-meant.
- `X-Brand-Max-Devices` / `X-Brand-Current-Devices` — without a current
  count the static chip read as filler; current count isn't reliably
  available across panels via response headers. Out of scope until the
  panel ecosystem catches up.

If a future use case actually demands one of these, it can be added under
`proposed` first and discussed.
