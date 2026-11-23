## AgenticClash

An Android graphical interface for [mihomo](https://github.com/MetaCubeX/mihomo), based on
[ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid).

This fork keeps the upstream profile engine and adds app routing strategies, reusable app sets,
WebDAV sync, and a loopback-only MCP server for local automation.

### Documentation

Architecture, build constraints, and product behavior live in the documentation system. Start at
[docs/INDEX.md](docs/INDEX.md). The agent guide is [AGENTS.md](AGENTS.md).

### Features

- mihomo core with the upstream Clash Meta feature set
- Per-app routing strategies and named app sets
- WebDAV sync for profiles and routing configuration
- Local MCP server bound to `127.0.0.1` for on-device agent tools
- Five-tab shell: Proxies, Profiles, Dashboard, Apps, Settings

### Requirements

- Android 5.0+ (minimum), Android 7.0+ (recommended)
- `arm64-v8a`
- JDK 21, Android SDK, CMake, and Go for building the native core

### Build

```bash
git submodule update --init --recursive
./gradlew :app:assembleMetaRelease
```

Create `local.properties` with `sdk.dir=/path/to/android-sdk`. Release signing reads
`signing.properties`; without it the release build falls back to the debug keystore. See the
[build guide](docs/guides/getting-started/build.md) for flavor names, signing keys, and the mihomo
submodule pin.

The application ID is `universe.agentic.device.clash` for both the `alpha` and `meta` flavors.

### Automation

- Toggle: action `universe.agentic.device.clash.action.TOGGLE_CLASH` on
  `com.github.kr328.clash.ExternalControlActivity`
- Start: action `universe.agentic.device.clash.action.START_CLASH`
- Stop: action `universe.agentic.device.clash.action.STOP_CLASH`
- Import a profile: `clash://install-config?url=<encoded URI>` or
  `clashmeta://install-config?url=<encoded URI>`

### Community

- Author: Clarence Henry, [Coolapk](https://www.coolapk.com/u/469700)
- WeChat Official Account: 超越观
- Telegram channel: [t.me/universesoftware](https://t.me/universesoftware)

### License

GPL-3.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
