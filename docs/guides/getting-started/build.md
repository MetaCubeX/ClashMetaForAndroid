---
title: "构建、配置与测试"
type: guide
status: accepted
phase: N/A
updated: 2026-10-15
summary: "Gradle 8.10.2、JDK 21、meta/alpha flavor、地理数据、签名回退和 mihomo 子模块 pin"
---

# 构建、配置与测试

数字来自当前 Gradle 文件与子模块检出。根 [README.md](../../../README.md) 仍写 OpenJDK 11 和 `assembleAlphaRelease`，那是上游步骤。本文件是本仓库的构建说明。

## 远程

当前只配置了 `origin`：`git@github.com:TIIEHenry/ClashMetaForAndroid.git`。没有名为 `upstream` 的远程。

## 工具链

| 项 | 值 | 出处 |
|:---|:---|:-----|
| Gradle | 8.10.2 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.8.0 | `gradle/libs.versions.toml` |
| Kotlin | 2.1.0 | 同上 |
| 编译目标 | Java 21 | 根 `build.gradle.kts` 的 `compileOptions` |
| minSdk / targetSdk | 21 / 35 | 根 `build.gradle.kts` |
| versionName / versionCode | 2.11.35 / 211035 | 同上 |
| NDK | 29.0.14206865 | 同上 |
| 原生 ABI 过滤 | 仅 `arm64-v8a` | `defaultConfig.ndk.abiFilters` 与 CMake `abiFilters` |

应用模块的 ABI split 仍列出四个 ABI，并且 `isUniversalApk = true`。原生库的过滤只放行 `arm64-v8a`。

## Flavor 与包名

`productFlavors` 的维度是 `feature`。`alpha` 是默认 flavor。未设置 `local.properties` 的 `remove.suffix=true` 时：

| Flavor | applicationId 后缀 | versionName 后缀 |
|:-------|:-------------------|:-----------------|
| alpha | `.alpha` | `.Alpha` |
| meta | `.meta` | `.Meta` |

基名是 `com.github.metacubex.clash`。meta 包名是 `com.github.metacubex.clash.meta`。应用的 `namespace` 是 `com.github.kr328.clash`，其他模块是 `com.github.kr328.clash.<模块名>`。`namespace` 与 `applicationId` 不是同一个字段。

两个 flavor 的 Java 源都使用 `src/foss/java`。`core` 的 Go 源也都指向 `core/src/foss/golang`。

装到手机上使用 meta 包时：

```bash
./gradlew :app:assembleMetaRelease
```

产物名以 `cmfa-2.11.35` 为前缀，meta release 的 versionName 带 `.Meta`。

## 地理数据

`app` 的每个 `assemble*` 任务依赖 `downloadGeoFiles`。任务把下列文件下载到 `app/src/main/assets/`（目录已在 `.gitignore` 中），文件已存在则跳过：

- `geoip.metadb`
- `geosite.dat`
- `ASN.mmdb`（上游文件名 `GeoLite2-ASN.mmdb`）
- `BundleMRS.7z`

来源是 `MetaCubeX/meta-rules-dat` 的 `latest` release。`:app:clean` 会删除整个 `app/src/main/assets`。国内应用名单不放这里，因为它会被 clean 清掉；名单在 `app/src/main/res/raw/domestic_packages.txt`。

## 本地配置与签名

`local.properties` 与 `signing.properties` 都被忽略，不进 Git。

```properties
sdk.dir=/path/to/android-sdk
```

可选：

```properties
custom.application.id=com.example.clash
remove.suffix=true
```

`signing.properties` 存在时，release 使用仓库根的 `release.keystore`，并读取 `keystore.password`、`key.alias`、`key.password`。文件不存在时，release 的 `signingConfig` 回退到 debug keystore。`*.keystore` 也被忽略。

## mihomo 子模块

`.gitmodules` 把 `core/src/foss/golang/clash` 指向 `https://github.com/MetaCubeX/mihomo` 的 `Alpha` 分支。`core/src/main/golang/go.mod` 用下面这一行替换模块：

```
replace github.com/metacubex/mihomo => ../../foss/golang/clash
```

子模块目录是空的，或没有 `go.mod` 时，`:core:externalGolangBuild*` 无法编译。

CMake 在 `core/src/main/cpp/CMakeLists.txt` 里执行 `git submodule foreach git branch -r --contains <commit>`。检出处于游离 HEAD，且没有任何远程跟踪分支包含该提交时，这一步拿不到分支名，配置失败。

当前子模块提交是 `90a5afcc06a174033538aa7c844917cf0933cade`，由本仓库里的 `origin/ca-bundle` 包含。它不一定在 GitHub 上的 `Alpha` 顶端。初始化后如果 CMake 报分支为空，需要有一个包含该提交的远程跟踪引用，而不是只留下游离 HEAD。

## 测试

WebDAV 合并、路径和客户端行为有 JVM 单元测试：

```bash
./gradlew :service:testMetaDebugUnitTest
```

源文件在 `service/src/test/java/com/github/kr328/clash/service/webdav/`。`design` 的单元测试同样带 flavor：

```bash
./gradlew :design:testMetaDebugUnitTest
```

默认 flavor 的对应任务把 `Meta` 换成 `Alpha`。这些命令是否在本次文档移植时跑过，见 [status.md](../../../dev/progress/status.md)。文档移植本身不把测试通过写成事实。
