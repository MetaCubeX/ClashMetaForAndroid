---
title: "构建、配置与测试"
type: guide
status: accepted
phase: N/A
updated: 2026-10-25
summary: "Gradle 8.10.2、JDK 21、共用 applicationId 的 meta/alpha flavor、地理数据、签名回退和 mihomo 子模块 pin"
---

# 构建、配置与测试

数字来自当前 Gradle 文件与子模块检出。根 [README.md](../../../README.md) 是英文项目介绍，构建细节以本文件为准。

## 远程

开源发布仓库是 `https://github.com/TIIEHenry/AgenticClash`。它从当前工作区重新初始化，不带旧 Git 历史；旧仓库 `TIIEHenry/ClashMetaForAndroid` 只作为上游对照，不作为发布远程。

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

`productFlavors` 的维度是 `feature`。`alpha` 是默认 flavor。两个 flavor 共用同一个 `applicationId`，不再加包名后缀：

| Flavor | applicationId | versionName 后缀 |
|:-------|:--------------|:-----------------|
| alpha | `universe.agentic.device.clash` | `.Alpha` |
| meta | `universe.agentic.device.clash` | `.Meta` |

`local.properties` 的 `custom.application.id` 仍可覆盖它。`remove.suffix=true` 只去掉 versionName 后缀。应用的 `namespace` 是 `com.github.kr328.clash`，其他模块是 `com.github.kr328.clash.<模块名>`。`namespace` 与 `applicationId` 不是同一个字段。定制界面、本地 MCP 的源码包名是 `universe.agentic.clash` 与 `universe.agentic.device.clash`。

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

来源是 `MetaCubeX/meta-rules-dat` 的 `latest` release。`:app:clean` 会删除整个 `app/src/main/assets`。国内应用名单和浏览器名单不放这里，因为会被 clean 清掉；名单在 `app/src/main/res/raw/domestic_packages.txt` 和 `app/src/main/res/raw/browser_packages.txt`。

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

`signing.properties` 存在时，release 读取 `keystore.password`、`key.alias`、`key.password`，密钥库文件固定为仓库根的 `release.keystore`，与上游相同。这份 `release.keystore` 继续进 Git。文件不存在时，release 的 `signingConfig` 回退到 debug keystore。其它 `*.keystore` 仍被忽略。

发布工作流不覆盖仓库里的 `release.keystore`，也不把 `signing.properties` 打到日志。`Build Release` 和 `Build Pre-Release` 调用 `scripts/write-signing-properties.py`，按 `java.util.Properties` 的转义规则写入口令、别名和 key 口令。口令里的反斜杠、`$`、反引号、`=`、`:` 和空白都能原样读回。三个 secret 缺一个，工作流在构建前失败：

- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

## mihomo 子模块

`.gitmodules` 把 `core/src/foss/golang/clash` 指向 `https://github.com/TIIEHenry/mihomo` 的 `ca-bundle` 分支。这是 [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo) 的 fork。`core/src/main/golang/go.mod` 用下面这一行替换模块：

```
replace github.com/metacubex/mihomo => ../../foss/golang/clash
```

子模块目录是空的，或没有 `go.mod` 时，`:core:externalGolangBuild*` 无法编译。

CMake 在 `core/src/main/cpp/CMakeLists.txt` 里执行 `git submodule foreach git branch -r --contains <commit>`。检出处于游离 HEAD，且没有任何远程跟踪分支包含该提交时，这一步拿不到分支名，配置失败。

对这个子模块的修改，提交或推送前要经过子代理审查。审查只看这次内核 diff。

当前子模块提交是 `e4c61f30d8011aa3eb6a0f8cb46b1d9356018c9a`，在 fork 的 `ca-bundle` 上。它修了 SOCKS UDP 关联、粘性会话、探活状态、Hysteria 重组、分享链接用户信息和 `http_simple` 空读。其下 `563a0da461ab1cdae3dd9c943d85fcd3ff4700f1` 记录 Android 构建已经在用的内核修改，父提交 `90a5afcc06a174033538aa7c844917cf0933cade` 加入 CA 证书包。`ca-bundle` 不是上游 `Alpha`。初始化后如果 CMake 报分支为空，在子模块里执行 `git fetch origin ca-bundle`，让 `origin/ca-bundle` 包含该提交。不要把 fork 的默认分支改成 `ca-bundle`：CMake 取 `git branch -r --contains` 的第一行，`origin/HEAD` 排在前面时版本字符串会带上 `HEAD`。

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
