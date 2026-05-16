# ClashFest Operator API — security

The Operator API is a **trust-bounded extension surface**. The client treats
every value as untrusted input from the network and applies hard validation
before it ever touches UI, theme, or disk.

This doc describes what the client guarantees, what it does not, and what
attacks we explicitly defend against.

## Threat model

The relevant attacker classes:

1. **Malicious operator** — controls the subscription URL response.
   - May send hostile headers (oversize values, payloads, malicious URLs).
   - Cannot be trusted with branding power alone; we treat them like any
     other external input.
2. **Network attacker** — intercepts subscription HTTP response on a
   compromised network (rare, since subscriptions are HTTPS).
3. **Operator-side image host compromise** — operator's logo CDN is taken
   over and starts serving hostile images / redirects to internal services.
4. **App-store reviewer / regulator** — needs to see that "branding" cannot
   silently transform the app into a different app (changing identity is
   limited to what the user sees on screen, not how the app behaves).

Note: an operator who is also the subscription provider can already abuse
many things outside this API (proxy choices, routing rules, etc.). Branding
just adds a few more knobs — those knobs must still be safe individually.

## What the client validates

### Strings

- Trimmed of leading / trailing whitespace
- Stripped of control characters (`U+0000`–`U+001F`, `U+007F`, except `\n` /
  `\r` / `\t` where the field allows it)
- Truncated to the per-field max length (see [headers.md](headers.md))
- Decoded from `base64:` prefix when present
- After all of that, blank → field treated as absent

### URLs

URL fields are accepted only when the value starts with one of:

- `https://`
- `tg://`
- `mailto:`
- `t.me/` (auto-promoted to `https://t.me/`)

`http://` is **rejected** for branding URLs (logos, links). The one
exception is metadata probes on subscription URLs themselves, where the
user explicitly opted in to a plaintext subscription.

URLs additionally pass a `looksLikeUrl` check (length, no embedded
whitespace).

### Hex colors

- Regex `^#[0-9A-Fa-f]{6}$` only — no `#RGB`, no `rgba()`, no named colors.
- The client computes WCAG luminance against the surface color. If contrast
  ratio is below 3:1 (the AA "large text" minimum), the color is rejected
  and the default theme accent is used.

### Booleans

`true` / `1` / `yes` / `on` (case-insensitive) → true.
`false` / `0` / `no` / `off` → false. Anything else → header treated as
absent.

### Enums

Only values from the documented set are accepted. Unknown values are
treated as if the header weren't there.

### Integers

- `Subscription-Userinfo` quota fields: parsed as `Long`, must be ≥0.
  `expire=` timestamp must be in `[0, now + 10 years]`; outside the window
  is treated as absent.
- `X-Brand-Max-Devices`: integer 1–999.

## Images (`X-Brand-Logo-URL`, `X-Brand-Logo-Dark-URL`)

This is the highest-risk field — we fetch arbitrary bytes from a
URL chosen by the operator. Defenses:

### Transport

- **HTTPS only.** http:// rejected before the request is even built.
- **No redirects to private networks.** Before each redirect hop is
  followed, the resolved IP is checked against:
  - `10.0.0.0/8`
  - `172.16.0.0/12`
  - `192.168.0.0/16`
  - `127.0.0.0/8`
  - `169.254.0.0/16` (link-local)
  - `::1/128` (IPv6 loopback)
  - `fc00::/7` (IPv6 ULA)
  - `fe80::/10` (IPv6 link-local)
  - Any other RFC1918 / RFC4193 / RFC6890 reserved space
- Connect + read timeouts: 15s each
- Max redirect count: 3
- Bound to the device's own DNS resolver (no system override during fetch)

### Response

- `Content-Type` must match one of `image/png`, `image/webp`, `image/jpeg`.
  Anything else (including `image/svg+xml`, `image/gif`, `text/html`) is
  rejected.
- `Content-Length` (when present) must be ≤ **512KB**. If absent, the
  read is capped at 512KB and any byte beyond aborts the download.
- After download, the bytes are parsed by `BitmapFactory.decodeByteArray`
  in **bounds-only mode first**, and a second decode only happens if
  `outWidth × outHeight × 4 ≤ 4MB` (max ~1024×1024 fully decoded).
- Animated images (animated WebP) are decoded as the first frame only.

### Storage

- Logos cached at `<filesDir>/brand/<sha256(url)>` (private to the app)
- Atomic write: `tmp` file + `rename`
- Purged when:
  - The owning subscription is deleted
  - A new logo for the same subscription arrives with a different URL
- Never written to external storage, never accessible to other apps

### Why no SVG

SVG can carry XSS-style payloads (`<script>`, `xlink:href` to remote
resources, CSS imports, foreignObject HTML). Even AndroidSVG's safer
parsing isn't a blanket guarantee against denial-of-service via billion-laughs
or geometry explosion. Logos are flag-bitmaps — PNG/WebP cover the use case
without the SVG attack surface.

> If a future operator strongly needs vector logos, we'd render them in a
> sandboxed renderer (e.g. parsed into a restricted set of shapes by us, no
> arbitrary XML evaluation) — not by piping their SVG into a general parser.

## What the client does **not** validate

- **Does the logo actually represent the operator?** No — we don't verify
  brand ownership. Operators can in principle show a logo from someone else
  (which would be fraud at the operator level, not our problem).
- **Is the operator's website actually theirs?** Same.
- **Does the brand name match the panel?** No — it's free-form.

These are operator-trust concerns, not client-security concerns. The user
already chose to trust this subscription by adding it.

## Identity changes are visible

By design, **branding never silently changes app identity** in ways the
user can't notice:

- The launcher icon and Android-level package name **never** change. App
  store reviewers can always identify the app.
- The About screen always shows the line `<brand> — powered by ClashFest`
  (untranslatable, fixed). The user can always tell what client they're
  running.
- When branding is active, Settings shows a "Reset branding" option that
  restores the default app identity in one tap.

## When branding resets

The cached brand (name, logo, accent, links) is purged in these cases:

- The active profile is removed (and there are no other profiles)
- The user taps "Reset branding" in Settings
- A subsequent fetch from the same subscription returns headers that no
  longer carry the brand value (clearing branding is an operator-side
  action, not stuck-forever state)

We do **not** reset branding on app upgrade — it survives version bumps.

## Rate / retry

- Logo fetches happen at most once per subscription update cycle
- Failed fetches are remembered for 1h to avoid retry storms
- The "active brand" cache in `SharedPreferences` is the source of truth
  between updates — losing network does not strip branding mid-session

## Reporting

If you find a security issue in the Operator API or its parser, please
follow the security policy at the top of the repository.
