# 构建和签名指南

本指南说明如何编译、签名并安装自定义的 CMFA Tasker 版本。

## 📦 配置说明

### 1. 包名配置

**自定义包名**：`com.github.kr328.clash.tasker`

**好处**：
- ✅ 与官方版本 `com.github.metacubex.clash.meta` 完全独立
- ✅ 可以同时安装官方版本和 Tasker 版本
- ✅ 互不干扰，方便测试和对比

**配置文件**：`local.properties`
```properties
# Android SDK 路径
sdk.dir=/Users/wuhanjian/Library/Android/sdk

# 自定义包名
custom.application.id=com.github.kr328.clash.tasker

# 移除后缀（最终包名不会有 .alpha 或 .meta）
remove.suffix=true
```

### 2. 签名配置

**Keystore 文件**：`tasker.keystore`
- **位置**：项目根目录
- **别名**：cmfa-tasker
- **有效期**：10000 天（约 27 年）

**配置文件**：`signing.properties`
```properties
keystore.path=tasker.keystore
keystore.password=cmfa2024tasker
key.alias=cmfa-tasker
key.password=cmfa2024tasker
```

⚠️ **注意**：`signing.properties` 包含敏感信息，**不要提交到 Git**！

## 🔨 构建步骤

### 方式一：命令行构建（推荐）

#### 1. 清理旧的构建文件

```bash
./gradlew clean
```

#### 2. 构建调试版本（快速测试）

```bash
# 构建调试版本（未签名）
./gradlew app:assembleAlphaDebug

# 输出位置
# app/build/outputs/apk/alpha/debug/app-alpha-debug.apk
```

#### 3. 构建发布版本（正式使用）

```bash
# 构建已签名的发布版本
./gradlew app:assembleAlphaRelease

# 输出位置
# app/build/outputs/apk/alpha/release/app-alpha-release.apk
```

**所有 ABI 版本**：
- `app-alpha-arm64-v8a-release.apk` - ARM 64位（大部分现代手机）
- `app-alpha-armeabi-v7a-release.apk` - ARM 32位（老旧手机）
- `app-alpha-x86-release.apk` - x86 32位（模拟器）
- `app-alpha-x86_64-release.apk` - x86 64位（模拟器）
- `app-alpha-universal-release.apk` - **通用版本（包含所有架构，推荐）**

#### 4. 安装到手机

```bash
# 方法1：通过 ADB 安装
adb install -r app/build/outputs/apk/alpha/release/app-alpha-universal-release.apk

# 方法2：发送到手机，手动安装
adb push app/build/outputs/apk/alpha/release/app-alpha-universal-release.apk /sdcard/Download/
```

### 方式二：Android Studio 构建

1. 用 Android Studio 打开项目
2. 在顶部工具栏选择 **Build** → **Select Build Variant**
3. 选择 `alphaRelease`
4. 点击 **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
5. 构建完成后，点击通知中的 **locate** 定位 APK 文件

## 📱 安装说明

### 首次安装

1. **传输 APK 到手机**
   ```bash
   adb push app/build/outputs/apk/alpha/release/app-alpha-universal-release.apk /sdcard/Download/
   ```

2. **在手机上安装**
   - 打开手机的"文件管理"应用
   - 找到 `/Download/app-alpha-universal-release.apk`
   - 点击安装
   - 可能需要允许"安装未知来源应用"

3. **查看安装结果**
   - 应用名称：Clash Meta Alpha（或 Clash Meta）
   - 包名：`com.github.kr328.clash.tasker`
   - 图标：与官方版本相同

### 验证安装

```bash
# 检查应用是否安装
adb shell pm list packages | grep tasker

# 应该输出
# package:com.github.kr328.clash.tasker
```

### 卸载（如果需要）

```bash
adb uninstall com.github.kr328.clash.tasker
```

## 🔄 更新版本

### 编译新版本

1. 修改代码
2. 重新构建：`./gradlew app:assembleAlphaRelease`
3. 覆盖安装：`adb install -r xxx.apk`

**注意**：由于使用相同的签名，可以直接覆盖安装，数据不会丢失。

## 🔐 签名验证

### 查看 APK 签名信息

```bash
# 方法1：使用 apksigner（推荐）
apksigner verify --print-certs app/build/outputs/apk/alpha/release/app-alpha-universal-release.apk

# 方法2：使用 jarsigner
jarsigner -verify -verbose -certs app/build/outputs/apk/alpha/release/app-alpha-universal-release.apk
```

### 查看 Keystore 信息

```bash
keytool -list -v -keystore tasker.keystore -storepass cmfa2024tasker
```

**关键信息**：
- **别名**：cmfa-tasker
- **算法**：RSA, 2048 位
- **有效期**：到 2052 年
- **所有者**：CN=CMFA Tasker

## 📊 包名对比

| 版本 | 包名 | 能否共存 |
|------|------|---------|
| 官方 Meta 版本 | `com.github.metacubex.clash.meta` | - |
| 官方 Alpha 版本 | `com.github.metacubex.clash.alpha` | - |
| **Tasker 自定义版本** | `com.github.kr328.clash.tasker` | ✅ 与官方共存 |

## 🎯 Tasker 配置（重要！）

**包名改变后，Tasker 配置需要更新：**

| 参数 | 值 |
|------|-----|
| **Action** | `com.github.metacubex.clash.meta.action.START_CLASH` |
| **Package** | **`com.github.kr328.clash.tasker`** ⚠️（注意：已改变！） |
| **Target** | Broadcast Receiver |
| **Class** | 留空 |

⚠️ **重要**：只有 **Package** 改变了，**Action** 保持不变！

## ⚡ 快速构建脚本

创建一个快速构建脚本 `build.sh`：

```bash
#!/bin/bash

echo "🧹 清理旧的构建..."
./gradlew clean

echo "🔨 构建发布版本..."
./gradlew app:assembleAlphaRelease

if [ $? -eq 0 ]; then
    echo "✅ 构建成功！"
    echo "📦 APK 位置："
    ls -lh app/build/outputs/apk/alpha/release/*.apk

    echo ""
    echo "📱 安装命令："
    echo "adb install -r app/build/outputs/apk/alpha/release/app-alpha-universal-release.apk"
else
    echo "❌ 构建失败！"
    exit 1
fi
```

使用：
```bash
chmod +x build.sh
./build.sh
```

## 🔍 常见问题

### Q1: 签名失败

**错误信息**：`Keystore was tampered with, or password was incorrect`

**解决方法**：
1. 检查 `signing.properties` 中的密码是否正确
2. 确认 `tasker.keystore` 文件存在且未损坏

### Q2: 安装失败：签名冲突

**错误信息**：`INSTALL_FAILED_UPDATE_INCOMPATIBLE`

**原因**：之前安装过不同签名的同包名应用

**解决方法**：
```bash
# 先卸载旧版本
adb uninstall com.github.kr328.clash.tasker

# 再安装新版本
adb install app/build/outputs/apk/alpha/release/app-alpha-universal-release.apk
```

### Q3: Gradle 下载慢

**解决方法**：配置国内镜像

编辑 `gradle/wrapper/gradle-wrapper.properties`：
```properties
# 使用腾讯云镜像
distributionUrl=https://mirrors.cloud.tencent.com/gradle/gradle-8.10.2-bin.zip
```

或使用阿里云镜像，编辑 `settings.gradle.kts`：
```kotlin
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}
```

### Q4: 构建时 Golang 编译失败

**确保已安装**：
- Golang (1.21+)
- Android NDK 27.2.12479018
- CMake

**检查环境变量**：
```bash
echo $ANDROID_HOME
echo $ANDROID_NDK_HOME
go version
```

## 📝 构建日志

构建过程的详细日志位于：
- `app/build/outputs/logs/`

如果构建失败，查看日志以诊断问题。

## 🎉 完成！

构建成功后，你就有了一个：
- ✅ 独立包名的 CMFA 版本
- ✅ 带 BroadcastReceiver 的完全后台控制
- ✅ 自己签名的 APK
- ✅ 可以与官方版本共存

现在可以安装并在 Tasker 中配置了！🚀
