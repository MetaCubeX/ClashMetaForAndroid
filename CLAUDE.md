# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

Clash Meta for Android (CMFA) 是 [Clash.Meta](https://github.com/MetaCubeX/Clash.Meta) 的 Android 图形用户界面。这是一个使用 Kotlin、Java 和 Go 编写的混合原生应用程序,利用 Clash Meta 核心提供代理服务。

## 构建命令

### 初始设置

```bash
# 更新子模块
git submodule update --init --recursive

# 创建 local.properties（如果不存在）
# sdk.dir=/path/to/android-sdk
# custom.application.id=com.my.compile.clash  # 可选：自定义包名
# remove.suffix=true  # 可选：移除应用ID后缀

# 创建 signing.properties（用于发布构建）
# keystore.path=/path/to/keystore/file
# keystore.password=<密钥库密码>
# key.alias=<密钥别名>
# key.password=<密钥密码>
```

### 构建变体

项目有两个产品风味：
- **alpha** - 默认风味，应用ID后缀为 `.alpha`
- **meta** - 应用ID后缀为 `.meta`

### 常用构建命令

```bash
# 构建 Alpha 发布版本
./gradlew app:assembleAlphaRelease

# 构建 Meta 发布版本
./gradlew app:assembleMetaRelease

# 构建调试版本
./gradlew app:assembleAlphaDebug

# 清理构建
./gradlew clean

# 下载地理数据文件（自动在构建时运行）
./gradlew downloadGeoFiles
```

### 构建多个 ABI

应用程序支持 4 个 ABI：`arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`。构建系统配置为为每个 ABI 生成单独的 APK 和一个通用 APK。

## 代码架构

### 模块结构

项目采用多模块架构，包含 6 个主要模块：

1. **app** - 主应用模块
   - 包含所有 Activity 和应用程序入口点
   - 依赖于所有其他模块（core、service、design、common、hideapi）
   - 处理 UI 交互和导航
   - 在构建时自动下载地理数据文件（geoip.metadb、geosite.dat、ASN.mmdb）

2. **core** - 核心 Clash 引擎模块
   - 包含 Golang 原生代码（在 `src/main/golang/native/` 中）
   - 使用 CMake 和 Golang 插件进行原生构建
   - 与 Clash.Meta 内核的 JNI 桥接
   - 编译为 `libclash.so` 原生库
   - Golang 构建标签：`foss`、`with_gvisor`、`cmfa`

3. **service** - 服务层模块
   - 实现 VPN 服务和后台处理
   - Room 数据库用于持久化存储
   - KAIDL 用于进程间通信
   - OkHttp 用于网络操作

4. **design** - UI 设计系统模块
   - 包含可重用的 UI 组件和主题
   - Material Design 组件
   - 数据绑定支持

5. **common** - 通用实用程序模块
   - 共享常量、实用程序和兼容性助手
   - 跨模块使用的数据存储提供程序
   - 日志记录基础设施

6. **hideapi** - 隐藏 API 访问模块
   - 提供对受限 Android API 的访问
   - 仅作为编译时依赖项

### 关键技术栈

- **Android SDK**: 最低 21（Android 5.0），目标 35
- **Kotlin**: 协程和序列化
- **数据绑定**: 用于 UI（hideapi 模块除外）
- **KSP**: 用于代码生成（Room、KAIDL）
- **Golang**: 用于核心 Clash 引擎（使用 `golang-android` 插件）
- **CMake**: 用于原生构建编排
- **Room**: 用于本地数据库
- **OkHttp**: 用于 HTTP 客户端

### Golang 原生集成

核心模块包含 Clash.Meta 的 Golang 实现：
- Golang 源代码位于 `core/src/main/golang/native/`
- 编译为共享库 `libclash.so`，用于所有 ABI
- JNI 桥接在 `core/src/main/cpp/` 和 `core/src/main/golang/native/bridge.c`
- CMake 协调 Golang 和 C/C++ 组件

### 内核维护

- Meta 内核来自 `MetaCubeX/Clash.Meta` 仓库的 `android-real` 分支
- `android-real` 是 `Alpha` 分支和 `android-open` 的合并
- 当 Meta 内核更新时，`Update Dependencies` 工作流会自动触发
- 工作流拉取新版本、更新 Golang 依赖项并创建 PR

## 发布管理

版本在 `build.gradle.kts` 中定义：
- `versionName`: "2.11.20"
- `versionCode`: 211020

GitHub Actions 工作流：
- `build-debug.yaml` - 调试构建
- `build-pre-release.yaml` - 手动触发预发布
- `build-release.yaml` - 手动触发发布（使用标签，例如 v1.2.3）
- `update-dependencies.yaml` - 自动依赖项更新

## 自动化 API

应用程序提供了两种用于外部控制的方式：

### 方式一：BroadcastReceiver（推荐）

**完全后台运行，不会触发任何界面，适合所有 ROM（包括 Flyme 等国产 ROM）。**

```kotlin
// 包名：com.github.metacubex.clash.meta
// 目标：BroadcastReceiver (ExternalControlReceiver)

// 切换服务
action: com.github.metacubex.clash.meta.action.TOGGLE_CLASH

// 启动服务
action: com.github.metacubex.clash.meta.action.START_CLASH

// 停止服务
action: com.github.metacubex.clash.meta.action.STOP_CLASH
```

**Tasker 配置示例：**
- **动作类型**：Send Intent
- **Action**：`com.github.metacubex.clash.meta.action.START_CLASH`
- **Target**：**Broadcast Receiver**（重要！）
- **Package**：`com.github.metacubex.clash.meta`

**优势：**
- ✅ 完全后台运行，不会弹出任何界面
- ✅ 所有 Android ROM 都支持，无特殊限制
- ✅ 无需 Root 权限

### 方式二：Activity（传统方式）

通过 `ExternalControlActivity` 控制，但可能在某些 ROM 上短暂显示界面：

```kotlin
// 目标 Activity：com.github.kr328.clash.ExternalControlActivity
// 使用相同的 Action
```

**Tasker 配置：**
- **Target**：Activity
- 其他参数与方式一相同

**注意：**在 Flyme 等国产 ROM 上可能会短暂弹出界面。

### URL Scheme

用于配置文件导入：
- `clash://install-config?url=<encoded URI>`
- `clashmeta://install-config?url=<encoded URI>`

### 典型自动化场景

**连接家庭 Wi-Fi 时自动关闭代理：**
- Profile 触发条件：State → WiFi Connected → SSID 选择家庭 Wi-Fi
- 关联 Task：发送 `STOP_CLASH` 广播

**离开家庭 Wi-Fi 时自动启动代理：**
- Profile 触发条件：State → WiFi Connected → 反选 Invert
- 关联 Task：发送 `START_CLASH` 广播

## 代码风格

使用项目的代码风格配置文件（`.idea/codeStyles/Project.xml`）：
- 在 Android Studio/IntelliJ IDEA 中：文件 → 设置 → 编辑器 → 代码风格 → 方案 → 项目

## 开发要求

- OpenJDK 21（sourceCompatibility 和 targetCompatibility 设置为 VERSION_21）
- Android SDK（最新）
- Android NDK 27.2.12479018
- CMake（用于原生构建）
- Golang（用于核心内核构建）
- Gradle（通过包装器：`./gradlew`）

## ProGuard 配置

每个模块在其目录中都有 `proguard-rules.pro`。在发布构建期间，应用程序模块启用了代码缩减和混淆。
