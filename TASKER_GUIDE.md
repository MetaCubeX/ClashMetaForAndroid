# Tasker 自动化配置指南

本指南详细说明如何在 Tasker 中配置 Clash Meta for Android (CMFA) 的自动化控制。

## 📌 重要提示

**使用 BroadcastReceiver 方式可以实现完全后台控制，不会弹出任何界面！**

## 前提条件

1. 已安装 CMFA（编译包含 ExternalControlReceiver 的版本）
2. 已安装 Tasker
3. 已授予 Tasker 必要的权限
4. **首次使用前，必须在 CMFA 中手动启动一次 VPN 并授予权限**

⚠️ **重要：首次 VPN 权限授予**

在使用 Tasker 自动化之前，必须：
1. 打开 CMFA 应用
2. 手动启动一次代理（会弹出 VPN 权限请求）
3. 授予 VPN 权限并勾选"记住选择"
4. 停止代理

**之后的 Tasker 自动化才能正常工作！**

## 方案一：BroadcastReceiver 方式（推荐）

### 优势
- ✅ **完全后台运行**，不会触发任何界面
- ✅ 适用于**所有 ROM**（包括 Flyme、MIUI、ColorOS 等国产 ROM）
- ✅ 无需 Root 权限
- ✅ 不受系统"后台启动限制"影响

### 步骤 1：创建启动 Clash 的 Task

1. 打开 Tasker，点击底部 **"TASKS"** 标签
2. 点击右下角 **"+"** 按钮，创建新任务
3. 输入任务名称：`启动 Clash`
4. 点击 **"+"** 添加动作
5. 选择 **System** → **Send Intent**
6. 填写以下参数：

   | 参数 | 值 |
   |------|-----|
   | **Action** | `com.github.metacubex.clash.meta.action.START_CLASH` |
   | **Cat** | 留空 |
   | **Mime Type** | 留空 |
   | **Data** | 留空 |
   | **Extra** | 留空 |
   | **Package** | `com.github.metacubex.clash.meta` |
   | **Class** | 留空（重要！） |
   | **Target** | **Broadcast Receiver**（非常重要！） |

7. 点击 **返回** ���存

### 步骤 2：创建停止 Clash 的 Task

重复步骤 1，但修改以下内容：
- 任务名称：`停止 Clash`
- **Action**：`com.github.metacubex.clash.meta.action.STOP_CLASH`
- 其他参数保持不变

### 步骤 3：创建切换 Clash 的 Task（可选）

如果你想要一个单键切换开关：
- 任务名称：`切换 Clash`
- **Action**：`com.github.metacubex.clash.meta.action.TOGGLE_CLASH`
- 其他参数保持不变

### 步骤 4：创建自动化 Profile

#### 场景 1：连接家庭 Wi-Fi 时自动关闭 Clash

1. 点击底部 **"PROFILES"** 标签
2. 点击右下角 **"+"** 创建新 Profile
3. 选择 **State** → **Net** → **Wifi Connected**
4. 在 **SSID** 字段输入你的家庭 Wi-Fi 名称（例如：`My Home WiFi`）
5. 点击返回
6. 在弹出的任务选择窗口中，选择 **`停止 Clash`**
7. 完成！当连接到指定 Wi-Fi 时，Clash 会自动停止

#### 场景 2：离开家庭 Wi-Fi 时自动启动 Clash

1. 长按上面创建的 Profile
2. 点击 **"Add Exit Task"**（添加退出任务）
3. 选择 **`启动 Clash`**
4. 完成！当断开指定 Wi-Fi 时，Clash 会自动启动

#### 场景 3：充电时启动，拔电时停止

**充电时启动：**
1. 创建新 Profile：**State** → **Power** → **Power**
2. 选择 **Any**（任何充电方式）
3. 关联任务：**`启动 Clash`**

**拔电时停止：**
1. 长按上面的 Profile
2. 点击 **"Add Exit Task"**
3. 选择 **`停止 Clash`**

#### 场景 4：特定时间段自动控制

**晚上 11 点自动关闭：**
1. 创建新 Profile：**Time** → 设置时间为 `23:00`
2. 关联任务：**`停止 Clash`**

**早上 7 点自动启动：**
1. 创建新 Profile：**Time** → 设置时间为 `07:00`
2. 关联任务：**`启动 Clash`**

### 步骤 5：测试

1. 手动运行任务：在 TASKS 界面，点击任务名称旁的播放按钮
2. 观察手机屏幕：**应该不会弹出任何界面**
3. 打开 CMFA 应用，检查服务状态是否改变
4. 触发 Profile 条件（如连接/断开 Wi-Fi），验证自动化是否生效

## 方案二：Activity 方式（传统方式）

**注意：** 此方式在 Flyme 等国产 ROM 上可能会短暂弹出界面，不推荐使用。

### 配置方法

与方案一基本相同，只需修改：
- **Target**：**Activity**（而非 Broadcast Receiver）
- **Class**：`com.github.kr328.clash.ExternalControlActivity`

## 常见问题

### Q1: 为什么还是会弹出界面？

**A:** 请确认以下几点：
1. 你编译的 APK 包含了 `ExternalControlReceiver`
2. Tasker 中 **Target** 设置为 **Broadcast Receiver**（不是 Activity）
3. **Class** 字段留空（非常重要！）

### Q2: 提示"找不到组件"或"Intent 发送失败"

**A:** 检查：
1. **Package** 是否正确：`com.github.metacubex.clash.meta`
2. **Action** 是否正确（区分大小写）
3. CMFA 是否已正确安装
4. 是否使用了包含 BroadcastReceiver 的版本

### Q3: 自动化不生效

**A:** 排查步骤：
1. 在 Tasker 中手动运行任务，看是否能控制 Clash
2. 检查 Profile 的触发条件是否正确
3. 确认 Tasker 有足够的权限（电池优化白名单、后台运行权限等）
4. 查看 Tasker 的日志（运行日志功能）

### Q4: 首次启动 VPN 时还是会弹���权限请求

**A:** 这是正常的。Android 要求用户首次授予 VPN 权限时必须有用户交互。解决方法：
1. 首次手动在 CMFA 中启动一次，授予 VPN 权限
2. 勾选"记住选择"或"不再提示"
3. 之后的自动化控制就不会再弹窗了

### Q5: 如何验证使用的是 BroadcastReceiver 方式？

**A:**
1. 运行 Tasker 任务
2. 如果屏幕**完全没有任何反应**（不闪屏、不弹窗），说明使用的是 BroadcastReceiver
3. 如果短暂看到 CMFA 界面，说明还是在使用 Activity 方式

## 高级技巧

### 结合其他条件

你可以在 Profile 中添加多个条件（AND 逻辑）：

**例如：工作日早上 8-18 点，且不在家庭 Wi-Fi 时，启动 Clash**

1. 创建 Profile
2. 添加条件 1：**Time** → 08:00 to 18:00
3. 点击左上角 **"+"** 添加条件 2：**Day** → 选择周一到周五
4. 再添加条件 3：**State** → **Wifi Connected** → **Invert**（反选）→ 输入家庭 Wi-Fi SSID
5. 关联任务：**`启动 Clash`**

### 创建桌面快捷方式

1. 长按任务
2. 选择 **"Create Widget"**
3. 拖动到桌面
4. 点击桌面图标即可一键控制 Clash

## 对比：BroadcastReceiver vs Activity

| 特性 | BroadcastReceiver | Activity |
|------|------------------|----------|
| 后台运行 | ✅ 完全后台 | ⚠️ 可能弹窗 |
| ROM 兼容性 | ✅ 所有 ROM | ⚠️ Flyme 等会前台化 |
| 实现复杂度 | 简单 | 简单 |
| 需要改源码 | ✅ 是（已完成） | ❌ 否（官方已支持） |
| 用户体验 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

## 总结

使用 **BroadcastReceiver 方式**，你可以在**任何 ROM**上实现**完全后台**的 Clash 自动化控制，不会有任何界面干扰。配置完成后，Clash 会根据你设定的条件（Wi-Fi、时间、充电状态等）自动启停，真正做到"无感知"自动化。

## 反馈

如果遇到任何问题，请检查：
1. Tasker 配置是否正确（特别是 Target 字段）
2. CMFA 版本是否包含 `ExternalControlReceiver`
3. 系统权限是否充足

祝你使用愉快！🎉
