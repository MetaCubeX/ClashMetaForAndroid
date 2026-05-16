# ClashFest branding — operator quick start

This is for **panel operators** who want their copy of ClashFest to look
like their service. The client reads HTTP response headers from your
subscription URL and adjusts itself accordingly.

If you're a panel developer who wants to surface these headers natively
in your admin UI, see [headers.md](headers.md) for the full reference.

## What you need

1. Your subscription URL is hit by clients with `GET <sub-url>`.
2. Your panel can add **custom HTTP response headers** to those responses.
   Every modern Clash panel supports this — see panel-specific instructions
   below.
3. A public HTTPS URL hosting your logo (PNG or WebP, 256×256 recommended,
   ≤200KB). ClashFest's default theme is dark, so a logo that looks right
   on dark is enough; if you also want a separate logo for users on the
   light theme, host both.

## Minimum viable branding

The minimum that gives a noticeable "branded" feel is three headers:

```
X-Brand-Name: SwiftVPN
X-Brand-Logo-URL: https://cdn.example.com/swiftvpn-logo-dark.png
X-Brand-Accent-Color: #5E35B1
```

After that, fill in operator info so the About screen looks like a real
business card:

```
X-Brand-Website-URL: https://swiftvpn.example.com
X-Brand-Support-URL: https://t.me/swiftvpn_support
X-Brand-Telegram-URL: https://t.me/swiftvpn_news
X-Brand-Bot-URL: https://t.me/swiftvpn_bot
X-Brand-Privacy-URL: https://swiftvpn.example.com/privacy
X-Brand-Terms-URL: https://swiftvpn.example.com/terms
X-Brand-Help-URL: https://swiftvpn.example.com/help
```

Optional — ship a light-theme logo variant for users who switched to the
light theme:

```
X-Brand-Logo-Light-URL: https://cdn.example.com/swiftvpn-logo-light.png
```

That's it for v1. v2 adds `X-Brand-Status-URL`, `X-Brand-Renew-URL`, and
the static `X-Brand-Max-Devices` chip. v3 adds the Hide-Stats / Hide-Logs /
Hide-Routing trio for simpler end-user UI.

## Panel-specific setup

> The exact UI labels change between panel versions. The principle is the
> same everywhere: find the "custom headers" section for subscription
> responses, paste the values.

### Pasarguard

1. Admin panel → **Settings** → **Subscription**
2. Scroll to **Custom Headers** (or **Response Headers**)
3. Add entries one per line:
   ```
   X-Brand-Name: SwiftVPN
   X-Brand-Logo-URL: https://cdn.example.com/swiftvpn-logo-dark.png
   X-Brand-Accent-Color: #5E35B1
   X-Brand-Support-URL: https://t.me/swiftvpn_support
   ```
4. Save.
5. Verify with `curl -I <subscription-url>` — the `X-Brand-*` headers
   should be in the response.

### Marzban

Marzban exposes subscription headers via the host-level template
configuration (`/etc/marzban/.env` or the configuration file your install
uses).

```env
SUB_PROFILE_TITLE="vasya@example.com"
SUBSCRIPTION_PAGE_TEMPLATE="subscription/page.html"
CUSTOM_TEMPLATES_DIRECTORY="/var/lib/marzban/templates/"
```

For arbitrary `X-Brand-*` headers, override the subscription router by
extending the response with `response.headers["X-Brand-Name"] = "SwiftVPN"`
in `app/subscription/v2ray.py` (or whichever response builder your version
uses). For most panel deployers the simpler path is to put a small
reverse-proxy (nginx / Caddy) in front and add the headers there — see
"Generic reverse proxy" below.

### Marzneshin

Marzneshin v0.5+ has the **"Subscription branding"** section in admin
under **Settings → Branding**. Paste the headers there directly.

For older versions, use the reverse proxy approach.

### Remnawave

Admin → **Hosts** → choose host → **Branding**. Each field maps to one
header. Save and recheck with `curl -I`.

### 3x-ui / X-UI

3x-ui has the **"Subscription Settings"** panel. Look for **Response
Headers** in the latest builds. If not present in your version, use the
reverse proxy approach.

### Generic reverse proxy (works with any panel)

If your panel doesn't expose custom headers, put nginx (or Caddy) in
front of your subscription route and have it inject the headers:

**nginx:**

```nginx
location /sub/ {
    proxy_pass http://localhost:8080;

    add_header X-Brand-Name "SwiftVPN" always;
    add_header X-Brand-Logo-URL "https://cdn.example.com/swiftvpn-logo-dark.png" always;
    add_header X-Brand-Accent-Color "#5E35B1" always;
    add_header X-Brand-Website-URL "https://swiftvpn.example.com" always;
    add_header X-Brand-Support-URL "https://t.me/swiftvpn_support" always;
    add_header X-Brand-Telegram-URL "https://t.me/swiftvpn_news" always;
    add_header X-Brand-Privacy-URL "https://swiftvpn.example.com/privacy" always;
    add_header X-Brand-Terms-URL "https://swiftvpn.example.com/terms" always;
}
```

The `always` flag is important — without it nginx skips the headers on
non-2xx responses.

**Caddy:**

```caddy
sub.swiftvpn.example.com {
    reverse_proxy localhost:8080

    header X-Brand-Name "SwiftVPN"
    header X-Brand-Logo-URL "https://cdn.example.com/swiftvpn-logo-dark.png"
    header X-Brand-Accent-Color "#5E35B1"
    header X-Brand-Website-URL "https://swiftvpn.example.com"
    header X-Brand-Support-URL "https://t.me/swiftvpn_support"
    header X-Brand-Telegram-URL "https://t.me/swiftvpn_news"
    header X-Brand-Privacy-URL "https://swiftvpn.example.com/privacy"
    header X-Brand-Terms-URL "https://swiftvpn.example.com/terms"
}
```

## Verifying

After deploying:

```bash
curl -I "https://your-domain.example/sub/<token>"
```

You should see your `X-Brand-*` headers in the response. If not, the
panel / proxy isn't sending them — check your config.

If the headers are there but the app doesn't react, the values likely
failed validation. Common issues:

- Logo URL is `http://` (must be `https://`)
- Logo file is too big (>512KB) or wrong type (not PNG / WebP / JPEG)
- Accent color isn't `#RRGGBB` exactly (no `#RGB`, no `rgba()`)
- Brand name has control characters or is >32 chars after trim

See [security.md](security.md) for the full validation rules.

## Reset behaviour

The user can hit **Settings → Reset branding** to remove your branding
and return to the default app identity. This isn't an attack — it's an
intentional escape hatch the app must offer (similar to "Reset to factory
defaults"). Your branding comes back on the next subscription fetch as
long as the headers are still being sent.

## What branding does NOT change

By design, the following remain ClashFest defaults regardless of branding:

- The Android launcher icon (the user installed "ClashFest" from the
  store and that's still what they have on their home screen)
- The Android package name and the app-store listing
- The "powered by ClashFest" line on the About screen
- The Settings → About → "App version" / build identifier

If you need a fully white-labelled app store presence (your own icon,
your own listing), that's a different scope — talk to us about a proper
fork / rebrand build.
