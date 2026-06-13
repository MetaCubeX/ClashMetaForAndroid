# ClashFest Roadmap

A living overview of what we're building, what's planned, what we're still
weighing, and what we won't do. Priorities shift with releases and community
feedback — open a [feature request](https://github.com/Nemu-x/ClashFest/issues/new/choose)
if something you need is missing.

> Legend: 🚀 coming soon · 🔭 exploring · 🟢 planned · 🚫 not planned · ✅ shipped
>
> Most ClashFest features are a UI on top of the **mihomo** core. Where an item
> says "core already supports it", the work is mainly interface + safety
> guard-rails; where it depends on the engine, that's called out explicitly.

---

## 🚀 Coming soon

| Item | Notes |
|------|-------|
| **Android TV + remote** | A TV-friendly layout of the same app, with the external controller doubling as a remote-control protocol. Large effort, but on the way. |

## 🔭 Exploring

| Item | Notes |
|------|-------|
| **Control dashboard** | A live home screen — real-time traffic, active connections, per-node latency (zashboard-style). |
| Custom rule-provider manager ([#98](https://github.com/Nemu-x/ClashFest/issues/98)) | Add/manage `mrs` / `yaml` / `text` rule-sets in-app. The core already supports those formats; today providers come from the subscription, and exclusions are done by rule order. |
| SDNS / DNSCrypt ([#69](https://github.com/Nemu-x/ClashFest/issues/69)) | `sdns://` resolvers aren't supported by the mihomo core today. Tracking upstream — if the core gains DNSCrypt, ClashFest follows. |

## 🟢 Planned

| Item | Notes |
|------|-------|
| Per-subscription UoT override | Force `udp-over-tcp` on supported nodes (the core already supports it per node). |

## 🚫 Not planned (out of scope)

| Item | Why |
|------|-----|
| Multi-node download accelerator ([#72](https://github.com/Nemu-x/ClashFest/issues/72)) | Range-sharding a single file across several nodes is a **download-manager** job, not a proxy function. A proxy routes connections; it doesn't split one file across outbounds. The core already does latency `url-test` and `load-balance` groups for connection distribution. |
| GeoIP-country-preferred load balancing ([#71](https://github.com/Nemu-x/ClashFest/issues/71)) | No such strategy exists in the core; it would need custom engine logic for a niche case. |
| `srs` / JSON ACL rule subscriptions ([#71](https://github.com/Nemu-x/ClashFest/issues/71)) | mihomo rule-providers use `yaml` / `text` / `mrs`. `srs` is sing-box's binary format (different engine); JSON ACL isn't a mihomo format. Convert lists to `mrs` / `yaml` instead. |
| Per-DNS-record-type routing ([#96](https://github.com/Nemu-x/ClashFest/issues/96)) | clash rules route **connections** (domain / IP / port / process), not DNS **queries by record type**. It's outside mihomo's model — the `DnsType_*` set isn't part of the core, and adding it would mean maintaining a custom engine fork. |
| sing-box-style routing actions ([#97](https://github.com/Nemu-x/ClashFest/issues/97)) | `sniff` / `resolve` / `hijack-dns` / `route-options` are **sing-box** rule actions. mihomo uses policies (`DIRECT` / `PROXY` / `REJECT` / `REJECT-DROP` / `PASS` / `COMPATIBLE`), not per-rule action objects. |
| "Fallback List" auto-learning ([#100](https://github.com/Nemu-x/ClashFest/issues/100)) | Auto-detecting a failed page load, retrying on the other mode, and silently rewriting the user's rules is opinionated and error-prone (defining "failed" reliably is the hard part). Out of scope for now. |

## ✅ Recently shipped

**0.8.0**
- **Reworked rule editor** ([#70](https://github.com/Nemu-x/ClashFest/issues/70)) — unified ordered list of user + subscription rules, intent-style add/edit, drag-reorder, dry-run before apply; subscription rules survive refresh by identity
- **DNS & Hosts editor** ([#69](https://github.com/Nemu-x/ClashFest/issues/69)) — per-profile nameservers + bootstrap, fake-ip mode, listen address, and `hosts:` (domain → IP)
- **Visual tunnels** ([#71](https://github.com/Nemu-x/ClashFest/issues/71)) — UI over the core `tunnels:` (protocol · listen · target · optional proxy), add/edit/delete with loopback guard-rails
- **Manual geo database update** ([#70](https://github.com/Nemu-x/ClashFest/issues/70)) — on-demand geoIP/geosite refresh from the configured source
- **Expert features** — an opt-in toggle that unlocks the raw config-override screen and power-user knobs; Advanced settings decluttered (dead/duplicate options removed)
- **Import robustness** — tolerate quirky values in subscription configs (e.g. quoted/whitespace `find-process-mode`) instead of failing the whole import; clearer fetch/parse error messages
- Round country flags on the active-node card (SVG with emoji fallback); bottom-sheet edge-to-edge fix; node-picker scroll fix
- Mihomo core updated to v1.19.27

**0.7.0**
- Profiles redesign — profiles in a dedicated tab with collapsible proxy groups
- Per-profile config viewer (read-only, YAML highlighting, copy/share)
- Simplified proxy chain (First hop → Exit, save vs use-now)
- Reworked update flow (in-app check, "About & updates", Home update indicator)
- Streamlined routing rules screen
- Mihomo core updated to v1.19.26; JNI panic hardening; performance and i18n fixes

---

*Have a request? File it via [Issues](https://github.com/Nemu-x/ClashFest/issues).
Please write issues in English where possible — it helps us triage and respond
faster.*
