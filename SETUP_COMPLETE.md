# 环境配置完成 ✅

## 🎯 问题总结

遇到的主要问题：
1. ❌ **NDK 版本问题** → ✅ 已安装 NDK 27.2.12479018
2. ❌ **Java 版本问题** → ✅ 已安装并配置 Java 21

## ✅ 已完成的配置

### 1. Java 环境
```bash
# 当前系统默认 Java
java -version  # Java 25.0.1

# 已安装 Java 21
/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# 项目使用 Java 21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

### 2. NDK 环境
```bash
# 已安装的 NDK 版本
$ANDROID_HOME/ndk/25.1.8937393/  # 原有版本
$ANDROID_HOME/ndk/27.2.12479018/ # 项目需要的版本 ✅
```

### 3. 项目配置
- ✅ 自定义包名：`com.github.kr328.clash.tasker`
- ✅ 签名配置：`tasker.keystore` 已创建
- ✅ BroadcastReceiver：已实现完全后台控制

## 🚀 构建命令

### 方式一：使用自动脚本（推荐）
```bash
./build.sh
```

### 方式二：手动构建
```bash
# 设置 Java 21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# 清理
./gradlew clean

# 构建
./gradlew app:assembleAlphaRelease
```

### 方式三：使用别名（一劳永逸）

将以下内容添加到 `~/.zshrc`：

```bash
# Clash Meta Tasker 项目
alias cmfa-java='export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home'
alias cmfa-build='cmfa-java && cd ~/Projects/personal/github/ClashMetaForAndroid && ./gradlew app:assembleAlphaRelease'
alias cmfa-clean='cmfa-java && cd ~/Projects/personal/github/ClashMetaForAndroid && ./gradlew clean'
```

重新加载配置：
```bash
source ~/.zshrc
```

之后只需运行：
```bash
cmfa-build  # 一键构建
```

## 📦 构建输出

构建成功后，APK 文件位于：
```
app/build/outputs/apk/alpha/release/
├── app-alpha-arm64-v8a-release.apk      # ARM 64位
├── app-alpha-armeabi-v7a-release.apk    # ARM 32位
├── app-alpha-x86-release.apk            # x86 32位
├── app-alpha-x86_64-release.apk         # x86 64位
└── app-alpha-universal-release.apk      # 通用版（推荐）
```

## 📱 安装

```bash
# 安装通用版（推荐）
adb install -r app/build/outputs/apk/alpha/release/app-alpha-universal-release.apk

# 或者特定架构版本（手机需要）
adb install -r app/build/outputs/apk/alpha/release/app-alpha-arm64-v8a-release.apk
```

## 🔧 Tasker 配置

**重要：包名已改变！**

| 参数 | 值 |
|------|-----|
| **Action** | `com.github.metacubex.clash.meta.action.START_CLASH` |
| **Package** | `com.github.kr328.clash.tasker` ⚠️ |
| **Target** | Broadcast Receiver |
| **Class** | 留空 |

详细配置请参考：`TASKER_GUIDE.md`

## 📚 完整文档

- `BUILD_GUIDE.md` - 详细的构建和签名指南
- `TASKER_GUIDE.md` - Tasker 配置指南
- `PR_GUIDE.md` - 如何贡献代码到上游
- `CLAUDE.md` - 开发者文档

## 🐛 常见问题

### Q: 为什么构建失败，提示 NDK 版本错误？

A: 确保已安装 NDK 27.2.12479018：
```bash
ls $ANDROID_HOME/ndk/27.2.12479018/
```

如果没有，使用 Android Studio → SDK Manager → SDK Tools 安装。

### Q: 为什么构建失败，提示 Java 版本错误？

A: 确保使用 Java 21：
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
java -version  # 应该显示 21.0.9
```

### Q: 如何验证构建成功？

A: 检查 APK 文件是否生成：
```bash
ls -lh app/build/outputs/apk/alpha/release/*.apk
```

应该看到 5 个 APK 文件。

### Q: 如何查看构建日志？

A: 构建日志保存在 `build.log` 文件中：
```bash
tail -f build.log  # 实时查看
```

## 🎉 完成！

所有配置已经完成，现在可以：
1. ✅ 编译自定义的 CMFA Tasker 版本
2. ✅ 与官方版本共存
3. ✅ 使用 BroadcastReceiver 实现完全后台控制
4. ✅ 贡献代码到上游仓库（不影响包名配置）

祝使用愉快！🚀
