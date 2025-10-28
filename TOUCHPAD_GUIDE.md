# Rokid 触摸板控制指南

## 🎮 触摸板按键映射

通过 `adb shell getevent -lp` 发现的触摸板支持：

```
设备名称：ROKID,PSOC-TP-R

支持的按键：
✅ KEY_ENTER     → 确认/点击（主要操作键）
✅ KEY_UP        → 向上导航
✅ KEY_DOWN      → 向下导航
✅ KEY_LEFT      → 向左导航
✅ KEY_RIGHT     → 向右导航
✅ KEY_BACK      → 返回
✅ KEY_DASHBOARD → 主页
```

## 🔧 ENTER 键触发修复

### 问题描述
Compose 默认不处理硬件按键事件，需要显式添加 `onKeyEvent` 处理。

### 修复方案

在按钮外层包裹 `Box`，添加按键事件监听：

```kotlin
Box(
    modifier = Modifier
        .focusRequester(focusRequester)
        .onFocusChanged { isFocused = it.isFocused }
        .onKeyEvent { keyEvent ->
            if (isFocused && 
                keyEvent.type == KeyEventType.KeyUp && 
                (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
            ) {
                onClick()  // 触发点击
                true
            } else {
                false
            }
        }
        .focusable()
) {
    Button(onClick = onClick) { ... }
}
```

### 关键点
1. **KeyEventType.KeyUp** - 在按键抬起时触发（避免重复触发）
2. **Key.Enter** - 标准 Enter 键
3. **Key.DirectionCenter** - D-pad 中心键（备用）
4. **isFocused 检查** - 只在有焦点时触发

## 🧪 测试 ENTER 键

### 方法 1：在设备上直接测试

1. 安装应用到 Rokid 设备
```bash
adb -s 1901092534000358 install -r app/build/outputs/apk/debug/app-debug.apk
```

2. 启动应用
```bash
adb -s 1901092534000358 shell am start -n com.jingbao.recorder/.MainActivity
```

3. 使用触摸板
   - 按钮应该有**绿色边框**（表示有焦点）
   - 按下触摸板的 **ENTER 键**
   - 按钮应该被触发

### 方法 2：通过 ADB 模拟按键

```bash
# 发送 ENTER 键事件
adb -s 1901092534000358 shell input keyevent KEYCODE_ENTER

# 或使用数字代码（66 = ENTER）
adb -s 1901092534000358 shell input keyevent 66
```

### 方法 3：实时监控按键

```bash
# 实时查看触摸板事件
adb -s 1901092534000358 shell getevent -lt

# 按下触摸板的按键，会看到类似输出：
# /dev/input/event1: EV_KEY       KEY_ENTER            DOWN
# /dev/input/event1: EV_KEY       KEY_ENTER            UP
```

## 🎯 应用操作流程

### 正常使用流程

```
1. 打开应用
   └─ 自动显示焦点（绿色边框）

2. 授予权限
   ├─ 按钮显示 "授予权限"
   ├─ 按 ENTER 键 ✅
   └─ 系统弹出权限请求

3. 开始录制
   ├─ 按钮显示 "开始录制"
   ├─ 按 ENTER 键 ✅
   ├─ 授予屏幕录制权限
   └─ 应用自动最小化到后台

4. 录制中
   └─ 可通过通知栏停止

5. 重新打开应用
   ├─ 按钮显示 "停止录制"
   ├─ 按 ENTER 键 ✅
   └─ 录制结束，保存视频
```

## 🐛 故障排除

### 问题 1：按 ENTER 键没有反应

**检查焦点**
```bash
# 查看应用日志
adb -s 1901092534000358 logcat -s RokidRecorderScreen:D

# 按钮是否有绿色边框？
# 没有 → 焦点问题
# 有 → 按键事件问题
```

**解决方案**
- 确保按钮可见且已渲染
- 尝试点击屏幕（如果有触摸屏）让应用获得焦点
- 重启应用

### 问题 2：焦点不在按钮上

**手动请求焦点**
```kotlin
LaunchedEffect(Unit) {
    focusRequester.requestFocus()
}
```

**解决方案**
- 应用会自动请求焦点
- 如果不行，尝试重启应用
- 检查是否有其他元素抢占了焦点

### 问题 3：按键事件被其他组件拦截

**查看按键事件**
```bash
# 实时监控按键
adb -s 1901092534000358 shell getevent -lt

# 如果看到 KEY_ENTER 事件但应用没反应
# → 事件被系统或其他组件拦截
```

**解决方案**
- 确保应用在前台
- 检查是否有系统弹窗
- 尝试按 BACK 键返回应用

## 📊 焦点状态调试

### 添加调试日志

在 `onFocusChanged` 中添加日志：

```kotlin
.onFocusChanged { focusState ->
    isFocused = focusState.isFocused
    Log.d("RokidRecorderScreen", "Focus changed: ${focusState.isFocused}")
}
```

### 查看日志

```bash
adb -s 1901092534000358 logcat -s RokidRecorderScreen:D

# 应该看到：
# Focus changed: true  ← 获得焦点
# Focus changed: false ← 失去焦点
```

## 💡 优化建议

### 1. 视觉反馈增强

当前方案：
- ✅ 焦点时绿色边框
- ✅ Button 原生点击效果

可以添加：
- 按下时的缩放动画
- 按下时的颜色变化
- 音效反馈

### 2. 支持更多按键

```kotlin
.onKeyEvent { keyEvent ->
    when {
        // ENTER 或 D-pad 中心
        keyEvent.key == Key.Enter || 
        keyEvent.key == Key.DirectionCenter -> {
            onClick()
            true
        }
        // BACK 键退出
        keyEvent.key == Key.Back -> {
            // 处理返回
            true
        }
        else -> false
    }
}
```

### 3. 多按钮导航

如果界面有多个按钮：

```kotlin
// 按钮 1
.onKeyEvent { keyEvent ->
    when (keyEvent.key) {
        Key.Enter -> { /* 触发按钮 1 */ }
        Key.DirectionDown -> { 
            focusRequester2.requestFocus() // 切换到按钮 2
            true 
        }
        else -> false
    }
}

// 按钮 2
.onKeyEvent { keyEvent ->
    when (keyEvent.key) {
        Key.Enter -> { /* 触发按钮 2 */ }
        Key.DirectionUp -> { 
            focusRequester1.requestFocus() // 切换到按钮 1
            true 
        }
        else -> false
    }
}
```

## ✅ 已实现的功能

- [x] ENTER 键触发按钮点击
- [x] D-pad 中心键触发按钮点击
- [x] 自动焦点管理
- [x] 焦点时绿色边框提示
- [x] KeyUp 事件避免重复触发
- [x] 焦点状态检查
- [x] 权限按钮和录制按钮都支持

## 🎉 测试结果

修复后的功能：
1. ✅ 打开应用，按钮自动获得焦点（绿色边框）
2. ✅ 按 ENTER 键，触发按钮点击
3. ✅ 权限请求弹出
4. ✅ 再次按 ENTER 键，开始录制
5. ✅ 应用自动后台，录制持续进行

**ENTER 键现在可以正常工作了！** 🎮✅

