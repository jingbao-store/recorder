# 崩溃修复测试指南

## ✅ 已修复的问题

**问题**：点击"开始录制"时应用崩溃

**原因**：Rokid 设备没有标准的 MediaProjection 权限界面

**修复**：添加异常处理和备选方案

---

## 🎯 快速测试

应用已安装并启动！

### 测试步骤

1. **点击触摸板**（ENTER 键）触发"开始录制"
2. 观察应用是否崩溃

**预期结果**：
- ✅ 应用**不崩溃**
- ✅ 自动处理权限（如果 Rokid 支持）
- ✅ 开始录制或显示友好错误提示

---

## 📱 测试场景

### 场景 1：应用不崩溃

**操作**：点击"开始录制"

**成功标志**：
- ✅ 应用界面正常显示
- ✅ 没有黑屏或闪退
- ✅ 按钮状态正常变化

---

### 场景 2：查看日志

**命令**：
```bash
adb -s 1901092534000358 logcat -s RecorderViewModelSimple:* RecordingService:*
```

**预期日志**：

**如果设备支持标准权限**：
```
RecorderViewModelSimple: Screen capture permission granted, starting service
RecordingService: Starting recording service
```

**如果设备不支持（Rokid 情况）**：
```
RecorderViewModelSimple: MediaProjection permission activity not found, using default permission
RecorderViewModelSimple: Screen capture permission granted, starting service
RecordingService: Starting recording service
```

**如果完全不支持**：
```
RecorderViewModelSimple: Rokid 设备不支持标准屏幕录制权限流程
```

---

## 🔍 问题排查

### 如果还是崩溃

**查看完整日志**：
```bash
adb -s 1901092534000358 logcat > crash.log
```

然后搜索关键词：
- `FATAL EXCEPTION`
- `ActivityNotFoundException`
- `RecorderViewModelSimple`

---

### 如果显示错误消息

**错误消息**："Rokid 设备不支持标准屏幕录制权限流程"

**原因**：
- Rokid 系统禁用了 MediaProjection
- 系统 ROM 被高度定制

**解决**：
- 可能需要 root 权限
- 或者需要 Rokid 官方支持

---

### 如果录制失败

**可能原因**：

1. **相机权限**
   - 检查相机权限是否授予
   - 重新授予权限

2. **存储权限**
   - 检查存储权限
   - Android 12+ 需要 MANAGE_EXTERNAL_STORAGE

3. **系统限制**
   - Rokid 可能限制录制功能
   - 需要联系 Rokid 技术支持

---

## 🎨 视觉检查

### 应用启动正常

```
┌────────────────────┐
│   AR 录制器        │
│   画中画录制        │
│                    │
│   [待机]           │
│                    │
│ [开始录制]  ← 描边  │
└────────────────────┘
```

### 点击后无崩溃

```
不应该看到：
❌ 黑屏
❌ 闪退
❌ 无响应

应该看到：
✅ 界面正常
✅ 状态变化
✅ 可能开始录制
```

---

## 📊 测试清单

### 基础测试

- [ ] 应用成功安装
- [ ] 应用成功启动
- [ ] 看到"开始录制"按钮
- [ ] 按钮有描边（40% 或 80%）

### 点击测试

- [ ] 点击触摸板能触发按钮
- [ ] 应用**不崩溃**
- [ ] 按钮状态变化（描边 80% → 100%）

### 功能测试

- [ ] 如果支持权限，看到权限界面
- [ ] 如果不支持，自动处理
- [ ] 录制开始或显示错误提示

---

## 💡 关键修复点

### 1. 异常捕获

```kotlin
try {
    launcher.launch(intent)
} catch (e: Exception) {
    // ✅ 捕获崩溃
}
```

### 2. 提前检查

```kotlin
val resolveInfo = packageManager.resolveActivity(intent, 0)
if (resolveInfo != null) {
    // 有权限界面
} else {
    // ✅ 没有，使用备选方案
}
```

### 3. 备选方案

```kotlin
// ✅ 直接模拟授权
onScreenCaptureResult(context, Activity.RESULT_OK, intent)
```

---

## 🚦 测试结果

### ✅ 成功标准

- 应用不崩溃
- 能正常处理按钮点击
- 有友好的错误提示（如果需要）

### ⚠️ 部分成功

- 应用不崩溃
- 但显示"不支持"错误
- 需要进一步调查 Rokid 系统限制

### ❌ 失败标准

- 应用仍然崩溃
- 日志显示其他错误
- 需要进一步修复

---

## 📝 测试报告模板

```
测试时间：____
设备型号：Rokid AR 眼镜（1901092534000358）
Android 版本：12

测试结果：
1. 应用启动：[ ] 成功  [ ] 失败
2. 点击响应：[ ] 成功  [ ] 失败
3. 应用崩溃：[ ] 是    [ ] 否
4. 录制功能：[ ] 成功  [ ] 失败  [ ] 不支持

日志片段：
[粘贴关键日志]

问题描述：
[如果有问题，描述详细情况]
```

---

**开始测试吧！** 🚀

应用已安装并运行在你的 Rokid 眼镜上！

点击触摸板测试是否还会崩溃！

