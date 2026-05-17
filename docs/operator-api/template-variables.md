# Template variables in `X-Brand-*` headers

Modern Clash panels (Remnawave, Pasarguard, Marzban, Marzneshin, 3x-ui)
let admins write template placeholders into custom response headers.
The panel substitutes them at request time, **before** sending the
response to ClashFest. From the client's point of view, the header
value arrives as a plain string — there's no client-side substitution
engine, every `X-Brand-*` value is treated as the final text.

This means **every header in this spec is already template-aware**.
Whatever your panel can interpolate into a string, you can interpolate
into a `X-Brand-*` header.

---

## How it works end-to-end

```
Panel admin enters in custom-headers UI:
  X-Brand-Tagline: Welcome {{USERNAME}}, {{DAYS_LEFT}} days remaining

Panel processes subscription request from user "vasya":
  X-Brand-Tagline: Welcome vasya, 12 days remaining

ClashFest receives that header and renders it on screen as-is.
```

The client never sees `{{USERNAME}}`. It only sees `vasya`.

---

## Panel cheat-sheet

> Variable names and semantics differ between panels. The lists below
> reflect what's documented at the time of writing — when in doubt,
> check your panel's custom-headers UI for the canonical list, or try
> a placeholder against a test subscription and inspect the response
> with `curl -I`.

### Remnawave

| Variable | What you (probably) get |
|---|---|
| `{{USERNAME}}` | Username configured in the admin panel |
| `{{EMAIL}}` | User email (if set) |
| `{{TELEGRAM_ID}}` | User's Telegram ID (if linked) |
| `{{TAG}}` | The free-form **user tag** set by the admin — typically a label like "premium-2024" or "vip". Format is whatever the admin entered; verify with `curl -I` |
| `{{STATUS}}` | Subscription status string (e.g. `active`, `expired`) |
| `{{DAYS_LEFT}}` | Integer — days until expiry |
| `{{TRAFFIC_USED}}` / `{{TRAFFIC_LEFT}}` / `{{TOTAL_TRAFFIC}}` | Human-formatted (e.g. `12.4GB`) |
| `{{TRAFFIC_USED_BYTES}}` / `{{TRAFFIC_LEFT_BYTES}}` / `{{TOTAL_TRAFFIC_BYTES}}` | Raw bytes integer |
| `{{LIFETIME_USED_BYTES}}` | Bytes used over user's lifetime |
| `{{EXPIRE_UNIX}}` | Unix epoch seconds of expiry |
| `{{CREATED_AT_UNIX}}` | Unix epoch seconds of account creation |
| `{{LAST_TRAFFIC_RESET_AT_UNIX}}` | Unix epoch seconds of last traffic reset |
| `{{RESET_STRATEGY}}` | Reset cadence (e.g. `daily`, `monthly`, `no_reset`) — verify in your panel |
| `{{SUBSCRIPTION_URL}}` | The subscription URL itself |
| `{{SHORT_UUID}}` | Short identifier |
| `{{ID}}` | Internal user ID |
| `{{SS_SUPPORT_LINK}}` | Panel-wide support link from settings |
| `{{SS_PROFILE_UPDATE_INTERVAL}}` | Recommended update interval (hours) |
| `{{SS_HWID_LIMIT}}` | HWID device limit |

### Pasarguard

| Variable | What you (probably) get |
|---|---|
| `{{PROFILE_TITLE}}` | Profile title |
| `{url}` | Subscription URL (lowercase, single braces in this panel) |
| `{format}` | Matched subscription format (`clash`, `v2ray`, etc.) |
| `{{USERNAME}}` | Username |
| `{{ADMIN_USERNAME}}` | Username of the admin who created the user |
| `{{SERVER_IP}}` / `{{SERVER_IPV6}}` | Server IP |
| `{{DATA_USAGE}}` / `{{DATA_LEFT}}` / `{{DATA_LIMIT}}` | Formatted (`12.4GB`) |
| `{{USAGE_PERCENTAGE}}` | Percent used (`53%`) |
| `{{DAYS_LEFT}}` | Integer days |
| `{{TIME_LEFT}}` | Formatted remaining time (`12d 5h`) |
| `{{EXPIRE_DATE}}` | Formatted Gregorian date |
| `{{JALALI_EXPIRE_DATE}}` | Formatted Jalali (Iranian) date |
| `{{STATUS_EMOJI}}` | Emoji indicating status (✅ / ⛔ / etc.) |

### Marzban / Marzneshin / 3x-ui

These panels typically expose a subset of the above; common names are
`{USERNAME}`, `{DATA_LIMIT}`, `{DATA_USED}`, `{DAYS_LEFT}`,
`{EXPIRE_DATE}`. Check the **Custom Headers** / **Subscription Headers**
section of your admin panel.

---

## Practical recipes

### Per-user help URL

```
X-Brand-Help-URL: https://help.example.com/u/{{USERNAME}}
```
Tap on Help in the Operator tab opens a user-specific help page,
pre-filled with their username — your support team gets context
without the user typing anything.

### Subscription-aware tagline

```
X-Brand-Tagline: {{DAYS_LEFT}} days · {{DATA_LEFT}} left
```
Tagline becomes a live status indicator right under the brand name.

### Plan badge

```
X-Brand-User-Tier: {{TAG}}
```
If your panel uses `TAG` for plan names (`premium-2024`, `trial`,
`vip`), it shows as a chip on the profile card. Reserve common values
like `Trial`, `Premium`, `Lifetime` for distinct chip colors — anything
else gets a neutral chip.

### Personalised greeting on Operator tab

```
X-Brand-Greeting: Welcome back, {{USERNAME}}!
```
Renders as the hero line on the Operator tab when the user opens it.
Combine with `{{DAYS_LEFT}}` for a dashboard-style summary:

```
X-Brand-Greeting: Hi {{USERNAME}} — {{DAYS_LEFT}} days, {{DATA_LEFT}} left
```

### Renew URL with referral / pre-filled token

```
X-Brand-Renew-URL: https://billing.example.com/renew?user={{USERNAME}}&token={{SHORT_UUID}}
```
Tap on the critical-expiry chip opens billing already pointing at the
user's record — no login flow on the web side.

### Support deep-link with auto-context

```
X-Brand-Support-URL: https://t.me/yoursupportbot?text=user%20{{USERNAME}}%20needs%20help
```
Telegram opens with a pre-typed message including the user's name.

### Status-aware logo (rare but possible)

```
X-Brand-Logo-URL: https://cdn.example.com/logos/{{STATUS}}.png
```
Different logos for active / expired / suspended states. **Caution:**
each unique URL produces a separate cache entry on disk; only do this
when the URL set is small (2–4 values).

---

## What client does **not** do

- ClashFest does not parse `{{...}}`. If a header arrives with literal
  `{{USERNAME}}` in it (because the panel didn't substitute), the
  client displays the literal text. Fix this on the panel side, not
  here.
- The client also does not know the user's username, email, etc.
  Anything you want shown has to come pre-substituted in a header.

## What client **does** validate after substitution

Every value still goes through the validators in [security.md](security.md):

- Max-length truncation (e.g. `X-Brand-Name` max 32 chars after the
  panel substitutes the variable — a username like
  `super-long-display-name-here-2024` will be cut to fit)
- HTTPS-only for URLs (no template can sneak through `http://`)
- Hex regex for accent color
- WCAG contrast filter for accent
- SSRF guard on logo URLs (template can't bypass private-IP rejection)

So a hostile / mis-configured substitution can't break out of the
existing safety envelope.

## Debugging

Run a quick check against your subscription URL after configuring
template variables:

```bash
curl -I -H "User-Agent: ClashforAndroid" https://your-domain.example/sub/<token>
```

Look at the `X-Brand-*` headers. If you see `{{...}}` literally,
substitution didn't fire — check the panel's logs / config. If you
see the right substituted value, ClashFest will pick it up on the
next subscription update.

## Related

- [`headers.md`](headers.md) — full spec of every `X-Brand-*` header
- [`branding-quickstart.md`](branding-quickstart.md) — operator
  tutorial for each panel
- [`security.md`](security.md) — validation rules applied to every
  parsed value
