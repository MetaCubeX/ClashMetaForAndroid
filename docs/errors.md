# Error Catalog (developer reference)

A map of the error messages a subscription can produce on its way from *import* to
*on-device activation*, which layer raises each one, what it actually means, and
what to do about it. Use this when a user reports a cryptic failure: match the
message text to a row, jump to the layer, act.

The journey of a subscription (each stage can fail with its own error class):

```
enforceFieldValid  →  fetch (network)  →  Parse (engine validate)  →  overlay/merge  →  Clash.load / ApplyConfig (on device)
   (Kotlin)             (Go + Kotlin)        (Go mihomo)                (Kotlin)              (Go mihomo)
```

Key files:
- `service/.../ProfileProcessor.kt` — orchestrates apply/update; `enforceFieldValid`.
- `service/.../util/FetchErrorClassifier.kt` — non-config-body → clear reason.
- `app/.../util/ImportRetry.kt` — transient network error retry classifier.
- `service/.../util/MergeEngineVerdict.kt` — merge-broke-it vs already-broken.
- `service/.../clash/module/ConfigurationModule.kt` — on-device load + recovery.
- `core/src/main/golang/native/config/{fetch,load}.go` — Go fetch/validate bridge.
- `core/src/foss/golang/clash/config/config.go` — mihomo `ParseRawConfig` (engine validation).

---

## Layer 1 — Pre-flight (Kotlin, `ProfileProcessor.enforceFieldValid`)

Cheap checks before any network. `IllegalArgumentException`.

| Pattern | Meaning | Cause | Action |
|---|---|---|---|
| `Empty name` | Profile name blank | Import form left name empty | UI validation; user fixes name |
| `Invalid url` | URL-type profile with empty source | Empty URL field | UI validation |
| `Unsupported url <src>` | Scheme not allowed | Not http/https/content, or not an allowed share source | Reject; guide user |
| `Invalid interval` | Auto-update interval < 15 min | Interval set below floor | Clamp to ≥ 15 min |
| `profile <uuid> not found` | Pending/imported row missing | Race: profile deleted mid-apply | Benign; abort apply |

---

## Layer 2 — Fetch (network) — Go `fetch.go` + Kotlin retry/classify

Timeouts: **subscription 60s** (single critical download), **each provider 20s**
(parallel, capped at 6 concurrent — `maxProviderConcurrency`). Provider fetch
errors are **swallowed** by design (a missing rule-provider must not abort import;
it re-fetches next pass). See `fetchProviders`.

### 2a. Transient — auto-retried once (`ImportRetry.withTransientRetry`, ~1.5s backoff)

These are retried automatically; user only sees them if the retry also fails.

| Pattern (in cause chain) | Cause | Notes |
|---|---|---|
| `UnknownHostException` / `No address associated with hostname` | Android DNS gave up after 3 tries | Cache hot on retry |
| `EOFException` / `unexpected end of stream` | HTTP/2 provider stream reset mid-body | Common while VPN tunnel active |
| `SocketTimeoutException` / `failed to connect` | Cold CDN edge ate the connect timeout | Warm pool on retry |
| `connection reset` / `stream reset` / `broken pipe` | Mid-stream reset | Second `update()` in flight can trigger this |

### 2b. Non-config body — clarified by `FetchErrorClassifier`

Server returned 200 but the body is **not YAML**. Without clarification mihomo
fails deep in Parse with a confusing `yaml: line N: ...` pointing at the error page.

| Detected body | Surfaced message |
|---|---|
| Blank | "the subscription server returned an empty response — try again later." |
| HTML (`<!doctype`, `<html`, `<title`, …) | "the subscription server returned a web page, not a config (likely rate-limited or an error page) — try again later." |
| Valid-looking YAML | original engine error kept (do not mask a real config error) |

### 2c. Other fetch failures (not retried)

| Pattern | Meaning | Action |
|---|---|---|
| `unsupported scheme <s> of <url>` | URL scheme not http/https/content | Reject at import |
| HTTP 4xx (kept as-is) | Auth / rate-limit / gone | Surface to user; not transient |

---

## Layer 3 — Engine validation (Go mihomo `ParseRawConfig`)

Runs after "Verifying". This is where a *downloaded* config is rejected. These are
the **precise engine messages** — the ones we deliberately preserve. Grouped by cause.

### 3a. YAML shape

| Pattern | Meaning |
|---|---|
| `yaml: line N: ...` / `cannot unmarshal ...` | Malformed YAML or a field of the wrong type. If body looked non-config, Layer 2b clarifies it first. |
| `configuration file <path> is empty` | Empty config after unmarshal |

### 3b. Proxies

| Pattern | Meaning |
|---|---|
| `proxy N: <err>` | Proxy at index N failed to parse |
| `missing type` | A proxy entry has no `type:` |
| `unsupport proxy type: <t>` | Unknown/unsupported outbound type |
| `proxy <name> is the duplicate name` | Two proxies share a name |

### 3c. Proxy groups (⚠ overlaps with our runtime recovery — see Layer 5)

| Pattern | Meaning |
|---|---|
| `proxy group N: missing name` | Group entry has no `name:` |
| `proxy group[N]: '<name>' not found` | Group references a proxy/provider that doesn't exist |
| `proxy group[N]: \`use\` or \`proxies\` missing` | Group has neither members nor providers |
| `proxy group[N]: unsupported type` | Unknown group type |
| `proxy group[N]: duplicate provider name` | Same provider listed twice in `use:` |
| `proxy group <name>: the duplicate name` | Group name collides with a proxy |
| `<group>: empty fallback proxy '<name>' not found` | `empty-fallback` target missing |

### 3d. Providers

| Pattern | Meaning |
|---|---|
| `parse proxy provider <name> error: <err>` | proxy-provider block invalid |
| `unsupport vehicle type` / `unsupported vehicle type: <t>` | provider `type:` not http/file/inline |
| `can not defined a provider called \`<reserved>\`` | Reserved provider name used |

### 3e. Rules / sub-rules

| Pattern | Meaning |
|---|---|
| `rules[N] [<line>] error: format invalid` | Malformed rule line |
| `rules[N] [<line>] error: proxy [<t>] not found` | Rule targets a missing proxy/group |
| `rules[N] [<line>] error: rule set [<name>] not found` | `RULE-SET` references undefined provider |
| `sub-rule name is empty` / `sub-rule[...] [<n>] not found` / `sub-rule error: circular references [...]` | sub-rule wiring broken |

### 3f. DNS

| Pattern | Meaning |
|---|---|
| `if DNS configuration is turned on, NameServer cannot be empty` | `dns.enable` but no nameserver |
| `DNS NameServer[N] format error / unsupport scheme` | Bad nameserver entry |
| `dns.fake-ip-range must be a IPv4 prefix` (and `...range6` IPv6) | Bad fake-ip range |
| `dns.fake-ip-filter[N] ... rule-set '<n>' not found` | fake-ip-filter references missing rule-set |
| `respect-rules ... proxy-server-nameserver cannot be empty` | respect-rules without proxy-server-nameserver |

### 3g. TUN / listeners / misc

| Pattern | Meaning |
|---|---|
| `when tun is enabled, iptables cannot be set automatically` | tun + auto-iptables conflict |
| `tproxy-port must be greater than zero` | Bad tproxy port |
| `tunnel proxy <name> not found` | `tunnels:` targets a missing proxy |
| `listener <name> is the duplicate name` | Duplicate listener |
| `external UI name is not local: <n>` | Bad external-ui-name |

---

## Layer 4 — Overlay / merge (Kotlin `MergeEngineVerdict`)

We compose the user's edit layer onto the fresh subscription, then **gate** the
result through the engine before applying. We never apply a config the engine rejects.

| Verdict | Meaning | What we do |
|---|---|---|
| `Ok` | Composed config validates | Apply composed config |
| `MergeIntroduced` | Fetched was valid, our overlay broke it → **our bug** | Fall back to clean fetched subscription; set update-engine warning |
| `PreexistingBroken` | Fetched was already invalid | Use fetched body as-is (breakage is the subscription's own) |

Log tells them apart:
- `Overlay broke a valid subscription for <uuid>; applying clean fetched instead.`
- `Composed config invalid ..., but fetched was already invalid (using fetched)`

---

## Layer 5 — On-device load / activation (Kotlin `ConfigurationModule` + Go `ApplyConfig`)

Runs when a profile is activated. `Clash.load(profileDir)` → native `Load` →
`ParseRawConfig` (Layer 3 patterns can re-appear here) → `hub.ApplyConfig` →
`loadProvider` initializes each provider over the network.

### 5a. Self-healing (retry up to 48×, then keep runtime alive)

| Detected | Recovery |
|---|---|
| `dialer-proxy` + (`not found` \| `circular`) | `ProxyDialerYamlEdit.clearAllDialerProxies` — strip all dialer-proxy, retry once |
| `proxy group[` + `'<name>' not found` | `ProxyGroupsYamlEdit.removeStaleNameFromAllProxyGroups` — drop the stale member, retry |

Stale references come from a subscription update that renamed/removed nodes still
referenced by a composed group. On unrecoverable load with no previously-loaded
profile, surfaces as `LoadException(message)`.

### 5b. Provider init at activation — **non-fatal, logged only**

`loadProvider` logs but does **not** abort:
- `initial proxy provider <name> error: <err>`
- `initial rule provider <name> error: <err>`

⚠ **Consequence:** if a subscription delivers all its nodes via a `proxy-providers:`
block whose URL is slow/geo-blocked *on the device's network*, `Initial()` times
out here → the group ends up **empty** → nothing routes, even though import
"succeeded". The failure lives in the **log**, not in a dialog. This is the prime
suspect for the "run timed out" report (see below).

---

## Layer 6 — User-facing UI strings (`design/.../strings.xml`)

Terminal messages shown as toasts/dialogs. These wrap the deeper errors above.

| String id | Text |
|---|---|
| `import_failed_title` | Could not import subscription |
| `toast_profile_updated_failed` | Could not update "%1$s": %2$s  ← `%2$s` is the underlying error |
| `profiles_update_all_failed` | Couldn't update %1$d subscription(s). |
| `profile_auto_refresh_failed_starting_saved` | Could not refresh now. Starting with the saved subscription. |
| `dns_hosts_err_invalid` / `tunnels_err_invalid` | The engine rejected this configuration. |
| `unable_to_start_vpn` / `vpn_start_failed_plain` | Unable to start VPN component / Could not start VPN. Open logs to see the reason. |
| `invalid_url` / `proxy_providers_invalid_url` | Invalid URL / Each row needs a valid http(s) URL |
| `yaml_preview_invalid` | Generated YAML is invalid. |
| `rules_hub_apply_failed` / `rule_snippet_apply_failed` | Could not apply rules |

---

## Investigation: "run timed out" — mechanism identified (2026-07-08)

**Report:** subscription **downloads fully** on import, but **on the device** it
fails / "run timed out".

**String finding:** the literal `run timed out` exists **nowhere** — not in our
Kotlin/Go, not in the mihomo submodule, not anywhere in the Go module cache
(`grep -rn "run timed out" ~/go/pkg/mod` → empty; the only near-hits are unrelated
`metacubex/ssh` *test* files). So it is **not** a string we or mihomo construct —
it is a paraphrase of what the user saw. The real event is a **provider fetch
timeout**, which the engine writes to the **Logs screen** as:

- `initial rule provider <name> error: ...` (executor `loadProvider`), and/or
- `[Provider] <name> pull error: <ctx deadline exceeded / status>` (`fetcher.updateWithLog`).

**Confirmed mechanism** (repro subscription: ~37 rule-providers, ~20 on
`raw.githubusercontent.com`, ~17 on `cdn.jsdelivr.net`, **every one** with
`proxy: <group>`):

1. **Import prefetches DIRECT, ignoring `proxy:`.** Our `fetchProviders`
   (`fetch.go`) downloads each provider URL with `openUrl` — a **plain direct
   HTTP GET with no proxy**. It never honors the provider's `proxy:` field (only
   mihomo's own vehicle does, via `WithSpecialProxy`). In RU, `raw.githubusercontent.com`
   (and increasingly `cdn.jsdelivr.net`) is DPI-blocked, so those direct GETs hit
   the 20s `providerFetchTimeout`, the error is **swallowed by design**, and the
   file is **never written**. Import still reports success (Parse only checks YAML
   shape) → "downloads fully" is only true for the subscription body + any
   reachable providers.

2. **Activation defers all the real work.** `Fetcher.Initial()` uses the on-disk
   file **if present** (`fetcher.go:57`); only a **missing** file falls through to
   `f.Update()` → fetch **through the proxy** (`WithSpecialProxy(h.proxy)`). So the
   ~20 GitHub-raw providers that never cached at import now each do a proxied fetch
   at load. `loadProvider` runs these under `wg.Wait()` **before** `tunnel.OnRunning()`,
   so it **blocks the tunnel from coming up**. Concurrency is arch-dependent
   (`concurrentCount`: **arm64 = unlimited**, **armv7/mips = 5/1**), so on a 32-bit
   device 20 providers serialize 5-at-a-time behind up-to-20s fetches.

3. **Result.** Either activation stalls for many seconds, or providers whose
   proxied fetch also fails end up as **empty rulesets** → rules referencing them
   don't match → traffic misrouted → "doesn't work on device", with timeout lines
   in the log.

**Root defect in our code:** the import-time `fetchProviders` is a partial,
proxy-blind **duplicate** of mihomo's own provider loading. For any provider that
declares `proxy:` it cannot succeed on a blocked host and is actively harmful
(20s × N of wasted blocking + false "cached" impression). `provider["proxy"]` is
available in the `fetchProviders` closure, so this is cheaply fixable.

**Fixes — decision & status:**
- **A — SHIPPED** (`fetch.go`): `fetchProviders` now **skips prefetch for any provider
  with a non-empty `proxy:`**. Our import fetch is a direct GET that can't honor the
  proxy anyway, so for a proxy-gated (often geo-blocked) host it only burned the 20s
  timeout and wrote no file. The engine fetches these correctly through the proxy on
  activation and keeps them fresh on its background pull loop.
- **B — DEFERRED** (decision 2026-07-08): making rule-provider `Initial()` non-blocking
  would require **forking the pinned mihomo submodule** (no `lazy` option exists for
  rule-providers) or a stub-file hack — both rejected against the "don't fork core /
  no crutches" rules. On arm64 `concurrentCount` is unlimited so the activation fetch
  is parallel (bounded ≈ one 20s window, usually faster when the proxy is up), which
  is tolerable. Revisit only if the activation stall proves painful on-device; if so,
  do it as a deliberate, documented core patch (like the XHTTP one).
- **C — SHIPPED** (`FetchErrorClassifier`): surfaced import/update failures now carry a
  clear reason + **stable code**. Precise engine (Layer 3) errors are still preserved
  untouched.

### Stable error codes (user-facing, `FetchErrorClassifier`)

| Code | Condition | Message gist |
|---|---|---|
| `E-10` | Body downloaded but blank | "server returned an empty response — try again later" |
| `E-11` | Body is an HTML error/rate-limit page | "server returned a web page, not a config" |
| `E-20` | Nothing downloaded + network-reach failure (DNS/TLS/connect timeout, host blocked) | "couldn't reach the subscription server — check your connection / host may be blocked" |
| `E-30` | Body is an age armor the engine couldn't decrypt (missing/wrong key) | "subscription is age-encrypted — import the full link from your dashboard, or set the profile's age secret key" |

`E-52` (provider-init timeout at activation) is **not** implemented: with B deferred,
those failures stay non-fatal log lines (`initial rule provider … error`), not surfaced
exceptions — surfacing them cleanly would need log-scraping, out of scope here.

**Still worth capturing** to close the loop: a Logs-screen snippet from the actual
device at activation, to confirm which providers time out and whether the proxied
fetch also fails (blocked-both-ways) vs just slow.

---

*Keep this in sync with the source when error strings change. Row = one distinct
pattern; if you add a new `fmt.Errorf` / string with user impact, add its row.*
