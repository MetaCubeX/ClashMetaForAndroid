# ClashFest Roadmap

A living overview of what we're building, what's planned, what we're still
weighing, and what we won't do. Priorities shift with releases and community
feedback — open a [feature request](https://github.com/Nemu-x/ClashFest/issues/new/choose)
if something you need is missing.

> Legend: 🛠️ in progress · 🟢 planned · 🔭 exploring · 🚫 not planned · ✅ shipped
>
> Most ClashFest features are a UI on top of the **mihomo** core. Where an item
> says "core already supports it", the work is mainly interface + safety
> guard-rails; where it depends on the engine, that's called out explicitly.

---

## 🛠️ In progress / next

| Item | Notes |
|------|-------|
| Import robustness | Tolerate quirky values in subscription configs (e.g. quoted/whitespace `find-process-mode`) instead of failing the whole import. |
| **DNS settings screen** ([#69](https://github.com/Nemu-x/ClashFest/issues/69)) | Per direct/proxy nameservers + bootstrap, fake-ip mode, listen address, cache algorithm, ECS. Maps onto the core `dns:` block. |
| **Visual tunnels** ([#71](https://github.com/Nemu-x/ClashFest/issues/71)) | UI for the core `tunnels:` feature (protocol · listen · target · optional proxy), with add/edit/delete and guard-rails. |
| **Reworked rule editor** ([#70](https://github.com/Nemu-x/ClashFest/issues/70)) | A cleaner, dedicated rule-set editor (the rule backend was kept intact). |

## 🟢 Planned

| Item | Notes |
|------|-------|
| Host redirection ([#71](https://github.com/Nemu-x/ClashFest/issues/71)) | UI over the core `hosts:` block (domain → IP). |
| Manual geo database update ([#70](https://github.com/Nemu-x/ClashFest/issues/70)) | On-demand geoIP/geosite refresh. |
| Per-subscription UoT override | Force `udp-over-tcp` on supported nodes (the core already supports it per node). |

## 🔭 Exploring (depends on the engine)

| Item | Notes |
|------|-------|
| SDNS / DNSCrypt ([#69](https://github.com/Nemu-x/ClashFest/issues/69)) | `sdns://` resolvers aren't supported by the mihomo core today. Tracking upstream engine support — if the core gains DNSCrypt, ClashFest follows. |

## 🚫 Not planned (out of scope)

| Item | Why |
|------|-----|
| Multi-node download accelerator ([#72](https://github.com/Nemu-x/ClashFest/issues/72)) | Range-sharding a single file across several nodes is a **download-manager** job, not a proxy function. A proxy routes connections; it doesn't split one file across outbounds. The core already does latency `url-test` and `load-balance` groups for connection distribution. |
| GeoIP-country-preferred load balancing ([#71](https://github.com/Nemu-x/ClashFest/issues/71)) | No such strategy exists in the core; it would need custom engine logic for a niche case. |
| `srs` / JSON ACL rule subscriptions ([#71](https://github.com/Nemu-x/ClashFest/issues/71)) | mihomo rule-providers use `yaml` / `text` / `mrs`. `srs` is sing-box's binary format (different engine); JSON ACL isn't a mihomo format. Convert lists to `mrs`/`yaml` instead. |

## ✅ Recently shipped (0.7.0)

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
