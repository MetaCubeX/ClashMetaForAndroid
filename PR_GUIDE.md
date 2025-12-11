# PR 贡献指南

本指南说明如何将 BroadcastReceiver 功能贡献给上游仓库。

## ❓ 为什么包名不会影响 PR

### 1. Git 忽略配置

```bash
# .gitignore 中已配置
local.properties      # 包含自定义包名配置
signing.properties    # 包含签名配置
*.keystore           # 密钥库文件
```

这些文件**不会被 Git 跟踪**，所以你的自定义包名不会被提交到仓库。

### 2. 代码中没有硬编码包名

我们创建的 `ExternalControlReceiver.kt` 不包含任何包名配置：

```kotlin
// ✅ 代码中没有硬编码包名
class ExternalControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intents.ACTION_START_CLASH -> { ... }  // 使用常量
            Intents.ACTION_STOP_CLASH -> { ... }
            Intents.ACTION_TOGGLE_CLASH -> { ... }
        }
    }
}
```

### 3. AndroidManifest.xml 使用固定 Action

```xml
<!-- ✅ 使用官方的 action 名称，与包名无关 -->
<receiver android:name=".ExternalControlReceiver">
    <intent-filter>
        <action android:name="com.github.metacubex.clash.meta.action.START_CLASH" />
        <action android:name="com.github.metacubex.clash.meta.action.STOP_CLASH" />
        <action android:name="com.github.metacubex.clash.meta.action.TOGGLE_CLASH" />
    </intent-filter>
</receiver>
```

### 4. 包名由构建系统动态决定

```kotlin
// Intents.kt 中的定义
val ACTION_START_CLASH = "$packageName.action.START_CLASH"
// $packageName 在运行时由系统提��，基于 AndroidManifest 的包名
```

## 📦 原仓库和你的仓库的区别

| 项目 | 原仓库 | 你的 Fork |
|------|--------|----------|
| **代码** | 完全相同 ✅ | 完全相同 ✅ |
| **包名（构建时）** | `com.github.metacubex.clash.meta` | `com.github.kr328.clash.tasker` |
| **包名配置** | 无 `local.properties` | 有 `local.properties`（不提交） |
| **签名** | 官方签名 | 你的签名（不提交） |

**结论**：代码层面没有任何区别，只是构建配置不同。

## 🚀 PR 准备步骤

### 方法一：使用准备脚本（推荐）

```bash
# 运行 PR 准备脚本
./prepare-pr.sh
```

这个脚本��：
1. 创建新的功能分支 `feature/broadcast-receiver-automation`
2. 只添加核心功能文件
3. 创建规范的提交信息

### 方法二：手动准备

#### 1. 创建功能分支

```bash
git checkout -b feature/broadcast-receiver-automation
```

#### 2. 查看修改的文件

```bash
git status
```

#### 3. 只添加核心功能文件

```bash
# 添加核心功能
git add app/src/main/java/com/github/kr328/clash/ExternalControlReceiver.kt
git add app/src/main/AndroidManifest.xml

# （可选）添加 build.gradle.kts 的改进
git add build.gradle.kts
```

#### 4. 检查将要提交的内容

```bash
git diff --staged
```

**确保不包含**：
- ❌ `local.properties`
- ❌ `signing.properties`
- ❌ `tasker.keystore`
- ❌ 任何自定义包名相关的内容

#### 5. 提交

```bash
git commit -m "feat: Add BroadcastReceiver for background automation control

Add ExternalControlReceiver for Tasker/automation tools to control
Clash service completely in background without triggering any UI.

Features:
- Works on all ROMs including Flyme, MIUI, ColorOS
- No screen flash or popup
- Same actions as ExternalControlActivity (START/STOP/TOGGLE)

Usage in Tasker:
- Action: com.github.metacubex.clash.meta.action.START_CLASH
- Target: Broadcast Receiver
- Package: com.github.metacubex.clash.meta
"
```

#### 6. 推送到你的 Fork

```bash
# 推送到你的 GitHub Fork
git push origin feature/broadcast-receiver-automation
```

#### 7. 创建 Pull Request

1. 访问你的 GitHub Fork
2. 点击 "Compare & pull request"
3. 填写 PR 描述

## 📝 PR 描述模板

```markdown
## 功能说明

添加 `ExternalControlReceiver`（BroadcastReceiver），用于 Tasker 等自动化工具的完全后台控制。

## 问题背景

当前的 `ExternalControlActivity` 在某些 ROM（如 Flyme、MIUI、ColorOS）上会短暂弹出界面，影响用户体验。

## 解决方案

使用 BroadcastReceiver 替代 Activity 作为自动化入口：
- BroadcastReceiver 完全在后台运行，不会触发任何 UI
- 不受 ROM 的"Activity 前台化"策略影响
- 与现有的 `ExternalControlActivity` 共存，保持向后兼容

## 实现细节

1. 新增 `ExternalControlReceiver.kt`
   - 接收三个 Action：START_CLASH、STOP_CLASH、TOGGLE_CLASH
   - 逻辑与 `ExternalControlActivity` 一致

2. 修改 `AndroidManifest.xml`
   - 注册新的 BroadcastReceiver
   - 使用相同的 Action 名称

3. 改进 `build.gradle.kts`（可选）
   - 从 `signing.properties` 动态读取 keystore 路径
   - 提高构建配置的灵活性

## 使用方法

### Tasker 配置

**原方式（Activity）**：
- Target: Activity
- Class: com.github.kr328.clash.ExternalControlActivity
- ⚠️ 某些 ROM 上会弹窗

**新方式（BroadcastReceiver）**：
- Target: **Broadcast Receiver**
- Class: 留空
- ✅ 所有 ROM 上完全后台

其他参数保持不变：
- Action: com.github.metacubex.clash.meta.action.START_CLASH
- Package: com.github.metacubex.clash.meta

## 测试

已在以下设备测试：
- [ ] 魅族 21 NOTE (Flyme 12 / Android 15) - 完全后台
- [ ] ���他设备...

## 向后兼容

✅ 完全向后兼容：
- `ExternalControlActivity` 继续保留
- 用户可以选择使用 Activity 或 BroadcastReceiver
- 不影响现有用户的配置

## 相关 Issue/Discussion

- #XXX (如果有相关 Issue 或讨论)
```

## ✅ 提交前检查清单

- [ ] 代码中没有硬编码自定义包名
- [ ] `local.properties` 没有被添加到 Git
- [ ] `signing.properties` 没有被添加到 Git
- [ ] `*.keystore` 没有被添加到 Git
- [ ] 提交信息清晰，说明了功能和使用方法
- [ ] 代码格式符合项目规范
- [ ] 测试通过（至少在一个设备上验证）

## 🔍 验证提交内容

```bash
# 查看将要提交的文件列表
git diff --staged --name-only

# 应该只包含：
# app/src/main/java/com/github/kr328/clash/ExternalControlReceiver.kt
# app/src/main/AndroidManifest.xml
# build.gradle.kts (可选)
```

## 🎯 PR 审查要点

维护者可能会关注：

1. **代码质量**
   - 是否遵循项目的代码规范
   - 注释是否清晰
   - 错误处理是否完善

2. **功能完整性**
   - 是否支持所有三个 Action
   - 是否与现有功能保持一致

3. **向后兼容**
   - 是否影响现有 API
   - 是否需要更新文档

4. **测试覆盖**
   - 是否在不同 ROM 上测试
   - 是否验证 Tasker 集成

## 📚 补充文档（可选）

如果 PR 被接受，可以考虑后续提交：
- 更新 README.md 的自动化 API 说明
- 添加 Tasker 配置示例
- 更新 GitHub Discussions 的相关帖子

## 🔄 后续维护

### 保持 Fork 同步

```bash
# 添加上游仓库
git remote add upstream https://github.com/MetaCubeX/ClashMetaForAndroid.git

# 拉取上游更新
git fetch upstream
git checkout main
git merge upstream/main

# 更新功能分支
git checkout feature/broadcast-receiver-automation
git rebase main
```

### 响应 PR 反馈

1. 根据维护者的反馈修改代码
2. 在同一个分支上提交新的 commit
3. 推送更新：`git push origin feature/broadcast-receiver-automation --force-with-lease`

## ✨ 总结

**你的自定义包名配置不会影响 PR，因为：**

1. ✅ `local.properties` 在 `.gitignore` 中，不会被提交
2. ✅ 代码中没有硬编码自定义包名
3. ✅ 功能代码与包名配置完全独立
4. ✅ PR 只包含核心功能文件，不包含个人配置

**你可以放心地：**
- 在本地使用自定义包名（与官方版本共存）
- 正常开发和测试
- 提交 PR 贡献功能（不会影响上游仓库）

祝 PR 顺利！🎉
