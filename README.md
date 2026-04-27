# ClashFest

<!--
https://img.shields.io/github/actions/workflow/status/Nemu-x/ClashFest/android-debug.yml?branch=feat/init-clashfest
-->

![License: GPL v3](https://img.shields.io/badge/license-GPLv3-green.svg)
![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84)
![Status: Active development](https://img.shields.io/badge/status-Active%20development-1f8bff)
![Latest Release](https://img.shields.io/github/v/release/Nemu-x/ClashFest?display_name=tag)
![Branch](https://img.shields.io/badge/branch-feat%2Finit--clashfest-blue)

**ClashFest** is an Android client in the **Clash Meta / Mihomo** family with a redesigned Home experience, cleaner subscription/node flows, routing-focused tools, and practical safety defaults for everyday use.

> **Status:** work in progress · **Active branch:** `feat/init-clashfest`

---

## Highlights

| Area | What you get |
|------|----------------|
| **Home** | Active node card, compact subscription actions, circular tab swipe, import from URL / QR / clipboard |
| **Modes** | Rule / Global quick switch from Home |
| **Rules & routing** | Dedicated Routing hub, rule snippets, effective rules, app routing (per‑app VPN policy) |
| **Network** | DNS / VPN options, security-oriented toggles where we wire them |
| **Features** | Safe “every‑day” toggles (unified delay, geodata mode, TCP concurrent) + entry to **Geo Data Source** |
| **Geo Data Source** | Presets for **geox-url** mirrors (same upstream data, different CDN paths), custom URLs, on-device geo DB import |
| **Subscriptions** | HTTP(S) / `content:` profiles; **`mierus://`** shares parsed via the same pipeline as other URL imports |
| **Quick start** | Quick Settings tile flow with VPN permission handling and one-tap startup |
| **Connections** | Live connections view with resilient snapshot decoding and lower polling load |
| **App** | Dark mode, optional **UI language** (system / EN / RU / ZH), notification & recents options |
| **Look** | Material 3 dark-first direction with refreshed ClashFest branding |

### Screenshots

<p align="center">
  <img src="docs/screenshots/home_on.png" width="180"/>
  <img src="docs/screenshots/home_off.jpg" width="180"/>
  <img src="docs/screenshots/node_list.png" width="180"/>
  <img src="docs/screenshots/profiles_main.jpg" width="180"/>
</p>

<p align="center">
  <img src="docs/screenshots/profiles_edit.jpg" width="180"/>
  <img src="docs/screenshots/routing.jpg" width="180"/>
  <img src="docs/screenshots/settings.jpg" width="180"/>
  <img src="docs/screenshots/themes.jpg" width="180"/>
</p>

## Current focus

- Stability and regression-free releases on top of the redesigned Home
- Routing and profile management polish without breaking upstream compatibility
- Performance and battery optimizations in high-refresh screens
- Localization quality (EN / RU / ZH first, others incremental)
- Post-release cleanup of medium-risk technical debt

---

## Repository layout

| Module | Role |
|--------|------|
| `app/` | Activities, navigation, packaging |
| `common/` | Shared helpers (imports, naming, ping helpers, …) |
| `core/` | JNI bridge, native **Mihomo** integration |
| `design/` | UI, themes, layouts, preference screens |
| `service/` | VPN service, profiles, rule merge / **Geo** presets |

---

## Build

### Requirements

- Android Studio (or compatible IDE)
- Android SDK + **NDK** as required by the project
- **JDK** matching the Gradle toolchain (see project / CI notes)
- Gradle wrapper (included)

### Debug (default **alpha** flavor)

**Linux / macOS**

```bash
./gradlew assembleAlphaDebug
```

**Windows**

```powershell
.\gradlew.bat assembleAlphaDebug
```

---

## Branding & docs

- App shield: `app/src/main/res/drawable-nodpi/clashfest_shield.png`
- Optional: `branding/` — source art · `docs/` — screenshots & notes

---

## License

Licensed under the **GNU General Public License v3.0**. See `LICENSE` and `NOTICE`.

---

## Disclaimer

ClashFest is provided **as-is**, without warranty. Use it responsibly and in compliance with local law, provider terms, and upstream licenses.

---

## Contributing

Right now development is focused on the ClashFest fork and branch workflow; contribution guidelines may expand later.

---

## Upstream & related projects

ClashFest builds on the open Clash / Meta stack. If you use or ship derivatives, keep **copyright and license notices** intact.

If ClashFest is useful for you, consider giving the project a star ⭐

| Project | What it is | Link |
|---------|----------------|------|
| **Clash Meta for Android** | Upstream Android client this tree forked from | [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) |
| **mihomo** | Clash.Meta core (Go) used under the hood | [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo) |
| **Meta rules dat** | Community geo / ruleset data releases (our **Geo Data Source** presets mirror these) | [MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat) |
| **Mieru** | UDP port hopping VPN protocol; **mierus://** subscription links are supported as imports | [enfein/mieru](https://github.com/enfein/mieru) |
| **SlothClash (Desktop)** | Companion desktop client in the same ecosystem (Wails · Go · React) | [Nemu-x/SlothClash](https://github.com/Nemu-x/SlothClash) |
| **Documentation** | Mihomo / Meta docs (rules, parsers, …) | [wiki.metacubex.one](https://wiki.metacubex.one/) |

