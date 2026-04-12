# Ninja Upgrade Checklist

这个仓库已经做过一轮 `ClashMetaForAndroid -> Ninja` 行为适配。后续同步上游时，优先检查下面这些位置，避免把现有行为冲掉。

## 1. Native so 与 ABI

- `app/src/main/jniLibs/`
  - 这里放的是 `Ninja` 的 `libbridge.so` 和 `libclash.so`
  - 如果上游重新产出 native so，不要直接覆盖这两份
- `build.gradle.kts`
  - 这里限制了 ARM ABI，并让打包优先吃 `jniLibs`
  - 升级后如果上游改了 `ndk/abiFilters/packagingOptions`，要重新核对

## 2. JNI / Bridge 适配

- `core/src/main/java/com/github/kr328/clash/core/bridge/Bridge.kt`
  - 保留和 `Ninja bridge` 对齐的 JNI 声明
  - 重点看：
    - `nativeStartTun`
    - `nativeInstallSideloadGeoip`
- `core/src/main/java/com/github/kr328/clash/core/Clash.kt`
  - 保留对旧版 `bridge` 接口的 Kotlin 适配
  - 重点看 `startTun(...)`

## 3. 订阅下载与私有协议处理

- `service/src/main/java/com/github/kr328/clash/service/ProfileProcessor.kt`
  - 这是最关键的文件
  - 需要保留：
    - `subscriptionUserAgent = "Ninja/2.10.4"`
    - 远程订阅先由 Kotlin 下载到 `processing/config.yaml`
    - `processNinjaProxies(...)`
    - 对远程 URL 调 `Clash.fetchAndValid(..., force = false)`，避免 native 再次重拉
- `service/src/main/java/com/github/kr328/clash/service/NinjaDecoder.kt`
  - 这是 `type: ninja` 私有节点的解码逻辑
  - 不能丢
- `service/src/main/java/com/github/kr328/clash/service/ProfileManager.kt`
  - 保留订阅请求头与订阅元信息解析改动

## 4. 订阅元信息与数据模型

- `service/src/main/java/com/github/kr328/clash/service/data/Imported.kt`
- `service/src/main/java/com/github/kr328/clash/service/data/Pending.kt`
- `service/src/main/java/com/github/kr328/clash/service/model/Profile.kt`
- `service/src/main/java/com/github/kr328/clash/service/data/Database.kt`
- `service/src/main/java/com/github/kr328/clash/service/data/migrations/Migrations.kt`

需要保留：
- `home`
- `crisp`
- 对应的数据库版本与 migration

如果上游再改表结构，记得把现有 migration 一起合并进去。

## 5. Sideload GeoIP

- `service/src/main/java/com/github/kr328/clash/service/store/ServiceStore.kt`
  - 保留 `sideloadGeoip`
- `service/src/main/java/com/github/kr328/clash/service/clash/module/SideloadDatabaseModule.kt`
- `service/src/main/java/com/github/kr328/clash/service/sideload/`
- `service/src/main/java/com/github/kr328/clash/service/ClashService.kt`
- `service/src/main/java/com/github/kr328/clash/service/TunService.kt`

这些是 `Ninja` 那套 sideload GeoIP 能力。

## 6. 升级后最容易回归的问题

- 订阅 `User-Agent` 又变回 `ClashMetaForAndroid/...`
- `ProfileProcessor` 被上游覆盖，导致远程订阅不再先下载再改写
- `NinjaDecoder` 没有接回导入/更新流程
- `jniLibs` 被新的构建产物覆盖
- 数据库 migration 被上游变更冲掉

## 7. 每次升级后的最小验证

1. 重新编译并安装 APK
2. 清空应用数据
3. 重新导入远程订阅
4. 检查运行态配置

用设备检查这两个点：

- `files/imported/<uuid>/config.yaml`
  - 应该是 `type: ninja`
- `files/processing/config.yaml`
  - 运行后也应该是 `type: ninja`
  - 不能再回到前置 `ss` 提示节点那份混合配置

最后再做一次真机连接测试，确认私有节点可用。

## 8. Ninja 上游更新时的最小替换流程

如果这次 `Ninja` 上游更新看起来只是核心更新，可以先按“只换 so”试一次，顺序如下：

1. 替换 `app/src/main/jniLibs/` 里的：
   - `libbridge.so`
   - `libclash.so`
2. 重新编译并安装 APK
3. 先确认应用能启动，没有 `UnsatisfiedLinkError` 或 JNI 签名错误
4. 清空应用数据
5. 重新导入远程订阅
6. 检查：
   - `files/processing/config.yaml` 是否仍然是 `type: ninja`
   - 私有节点是否还能正常连接

如果这几步都正常，这次更新基本可以视为“只换 so 就够了”。

如果失败，再按顺序排查：

1. `Bridge.kt` / `Clash.kt`
   - JNI 接口是否和新的 `libbridge.so` 仍然兼容
2. `ProfileProcessor.kt`
   - 远程订阅下载和 `processNinjaProxies(...)` 是否仍然适配新的返回格式
3. `NinjaDecoder.kt`
   - 私有 `type: ninja` 的编码/解码规则是否发生变化

一句话：
- 默认先试“只换 `libbridge.so + libclash.so`”
- 失败了再补 Kotlin 层适配

## 9. Meta 上游更新时怎么处理

`Meta` 上游更新不要按“只换 so”处理。

因为当前这套工程是：
- 上层和工程结构主要来自 `ClashMetaForAndroid`
- 核心 so 和部分行为适配来自 `Ninja`

所以同步 `Meta` 上游时，正确顺序是：

1. 先合并 `Meta` 上游代码
2. 处理冲突
3. 按本清单把 `Ninja` 相关改动重新核一遍
4. 重新编译安装
5. 清空应用数据并重新导入订阅验证

### Meta 上游更新后重点回看的文件

- `build.gradle.kts`
- `app/src/main/jniLibs/`
- `core/src/main/java/com/github/kr328/clash/core/bridge/Bridge.kt`
- `core/src/main/java/com/github/kr328/clash/core/Clash.kt`
- `service/src/main/java/com/github/kr328/clash/service/ProfileProcessor.kt`
- `service/src/main/java/com/github/kr328/clash/service/ProfileManager.kt`
- `service/src/main/java/com/github/kr328/clash/service/NinjaDecoder.kt`
- `service/src/main/java/com/github/kr328/clash/service/store/ServiceStore.kt`
- `service/src/main/java/com/github/kr328/clash/service/data/Database.kt`
- `service/src/main/java/com/github/kr328/clash/service/data/migrations/Migrations.kt`
- `service/src/main/java/com/github/kr328/clash/service/clash/module/SideloadDatabaseModule.kt`

### Meta 上游更新后的最小验证

1. 编译并安装 APK
2. 清空应用数据
3. 重新导入远程订阅
4. 检查：
   - `files/imported/<uuid>/config.yaml` 是 `type: ninja`
   - `files/processing/config.yaml` 也是 `type: ninja`
5. 测一个私有节点，确认还能正常连接

一句话：
- `Ninja` 上游更新，先试“换 so”
- `Meta` 上游更新，先合并代码，再重套这份适配
