# Clash Meta AI

A modified build of [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)
with a built-in AI assistant that can read and change the client's configuration
by conversation.

**This is not the upstream project and is not endorsed by MetaCubeX.** Forked from
upstream `v2.11.32`; modifications began August 2026. Licensed under
[GPL-3.0](LICENSE) like upstream — this repository is the corresponding source for
any build made from it. Upstream's own README follows below.

## What this fork adds

**AI assistant** — a tool-calling agent with 32 operations over profiles, per-app
routing, VPN settings, proxy groups, providers, connections and logs. It reads state
before writing, validates every profile change against the bundled core, keeps
backups and rolls back on failure. Works with any OpenAI-compatible endpoint
(Chat Completions or Responses); you supply the URL, model and key.

Three approval modes decide what runs unattended. Read-only operations always do;
under the default *balanced* mode config edits and VPN changes stop and ask. The
gate is enforced in code from each tool's declared risk level, so no prompt can
talk its way past it. Every run shows exactly which tools executed and whether
they succeeded — the header states `已完成 · 写入 N 项` or `未修改任何配置`, taken
from the tool results rather than from what the model claims.

**Proxy node details** — nodes carry a protocol badge, and long-pressing one opens
its full configuration: cipher, transport, TLS, SNI, fingerprint and so on, read
live from the core. Chained nodes render as a hop-by-hop stepper. Credentials are
masked before they leave the core.

**Reliability** — records why the VPN process died (low-memory kill, ANR, crash,
user stop) and recovers a service the system killed unexpectedly.

## Install

Grab an APK from [Releases](https://github.com/viewer12/ClashMetaForAndroid/releases).
The package is `io.github.viewer12.cmfa.agent`, so it installs alongside an existing
Clash Meta rather than replacing it. Builds are signed with this fork's own key and
cannot update an upstream install.

## Privacy

The assistant sends whatever it needs to the endpoint **you** configure — and for
profile edits that includes the full YAML, proxy passwords and subscription URLs
among it. Nothing is sent anywhere until you configure a model, and nothing is ever
sent to this project. Read [PRIVACY_POLICY.md](PRIVACY_POLICY.md) before enabling it.

## Build

```bash
git submodule update --init --recursive
./gradlew :app:assembleAgentDebug -PagentArm64Only=true
```

Needs JDK 21, the Android SDK, CMake and Go. `-PagentArm64Only=true` builds only
arm64-v8a, which is much faster. Release builds need your own signing key — run
`scripts/generate-release-key.sh` and see [docs/SIGNING.md](docs/SIGNING.md).

---

## Clash Meta for Android

A Graphical user interface of [Clash.Meta](https://github.com/MetaCubeX/Clash.Meta) for Android

### Feature

Feature of [Clash.Meta](https://github.com/MetaCubeX/Clash.Meta)

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.github.metacubex.clash.meta/)

### Requirement

- Android 5.0+ (minimum)
- Android 7.0+ (recommend)
- `armeabi-v7a` , `arm64-v8a`, `x86` or `x86_64` Architecture

### Build

1. Update submodules

   ```bash
   git submodule update --init --recursive
   ```

2. Install **OpenJDK 11**, **Android SDK**, **CMake** and **Golang**

3. Create `local.properties` in project root with

   ```properties
   sdk.dir=/path/to/android-sdk
   ```

4. (Optional) Custom app package name. Add the following configuration to `local.properties`.

   ```properties
   # config your ownn applicationId, or it will be 'com.github.metacubex.clash'
   custom.application.id=com.my.compile.clash
   # remove application id suffix, or the applicaion id will be 'com.github.metacubex.clash.alpha'
   remove.suffix=true

5. Create `signing.properties` in project root with

   ```properties
   keystore.path=/path/to/keystore/file
   keystore.password=<key store password>
   key.alias=<key alias>
   key.password=<key password>
   ```

6. Build

   ```bash
   ./gradlew app:assembleAlphaRelease
   ```

### Automation

APP package name is `com.github.metacubex.clash.meta`

- Toggle Clash.Meta service status
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.github.metacubex.clash.meta.action.TOGGLE_CLASH`
- Start Clash.Meta service
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.github.metacubex.clash.meta.action.START_CLASH`
- Stop Clash.Meta service
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.github.metacubex.clash.meta.action.STOP_CLASH`
- Import a profile
  - URL Scheme `clash://install-config?url=<encoded URI>` or `clashmeta://install-config?url=<encoded URI>`

### Contribution and Project Maintenance

#### Meta Kernel

- CMFA uses the kernel from `android-real` branch under `MetaCubeX/Clash.Meta`, which is a merge of the main `Alpha` branch and `android-open`.
  - If you want to contribute to the kernel, make PRs to `Alpha` branch of the Meta kernel repository.
  - If you want to contribute Android-specific patches to the kernel, make PRs to  `android-open` branch of the Meta kernel repository.

#### Maintenance

- When `MetaCubeX/Clash.Meta` kernel is updated to a new version, the `Update Dependencies` actions in this repo will be triggered automatically.
  - It will pull the new version of the meta kernel, update all the golang dependencies, and create a PR without manual intervention.
  - If there is any compile error in PR, you need to fix it before merging. Alternatively, you may merge the PR directly.
- Manually triggering `Build Pre-Release` actions will compile and publish a `PreRelease` version.
- Manually triggering `Build Release` actions will compile, tag and publish a `Release` version.
  - You must fill the blank `Release Tag` with the tag you want to release in the format of `v1.2.3`.
  - `versionName` and `versionCode` in `build.gradle.kts` will be automatically bumped to the tag you filled above.
