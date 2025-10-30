# UI 状态恢复修复

## 问题描述

当用户回到应用时，即使录制还在后台进行，UI 也会显示"待机"状态，而不是"录制中"。

### 复现步骤

1. 开始录制
2. 按 Home 键进入后台
3. 重新打开应用
4. **问题**：UI 显示"待机"，但录制实际还在进行

## 原因分析

### 应用生命周期

```
开始录制 -> 进入后台 -> 回到前台
   ↓           ↓            ↓
Service    ViewModel    新的 ViewModel
运行中      被清理        状态=IDLE
   ↓           ↓            ↓
录制继续    广播断开      UI 错误显示
```

### 详细流程

1. **应用进入后台**：
   - Activity 被销毁
   - ViewModel 被清理 (`onCleared()`)
   - 广播接收器被注销
   - UI 状态丢失

2. **RecordingService 继续运行**：
   - Service 是前台服务，不会被杀死
   - 相机、音频、编码器都在正常工作
   - 但没有 UI 连接了

3. **回到应用时**：
   - 创建新的 Activity 和 ViewModel
   - 状态初始化为 `RecordingState.IDLE`
   - 注册新的广播接收器
   - **但 Service 不会主动发送当前状态**
   - 所以 UI 保持 IDLE 状态

## 解决方案

### 在 ViewModel 初始化时检查 Service 状态

```kotlin
fun registerReceivers(context: Context) {
    // ... 注册广播接收器 ...
    
    // ✅ 检查 Service 是否正在运行，如果是则恢复状态
    if (isRecordingServiceRunning(context)) {
        Log.d(TAG, "RecordingService is running, restoring state to RECORDING")
        _recordingState.value = RecordingState.RECORDING
    }
}

private fun isRecordingServiceRunning(context: Context): Boolean {
    try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (RecordingService::class.java.name == service.service.className) {
                return true
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error checking if service is running", e)
    }
    return false
}
```

## 修改的文件

### `app/src/main/java/com/jingbao/recorder/viewmodel/RecorderViewModelSimple.kt`

1. **添加导入**：`import android.app.ActivityManager`
2. **新增方法**：`isRecordingServiceRunning()` - 检查 Service 是否运行
3. **修改方法**：`registerReceivers()` - 注册后检查并恢复状态

## 工作流程

### 修复前

```
用户返回应用
    ↓
创建新 ViewModel
    ↓
状态 = IDLE
    ↓
注册广播接收器
    ↓
等待广播... (永远不会来，因为 Service 不知道有新接收器)
    ↓
UI 错误显示 ❌
```

### 修复后

```
用户返回应用
    ↓
创建新 ViewModel
    ↓
状态 = IDLE
    ↓
注册广播接收器
    ↓
检查 Service 是否运行 ✓
    ↓
Service 正在运行 -> 状态 = RECORDING
    ↓
UI 正确显示 ✅
```

## 注意事项

### 关于 `getRunningServices()` 的废弃警告

`ActivityManager.getRunningServices()` 在 Android O (API 26) 被标记为废弃，但：

1. **仍然可用**：该方法仍然可以查询自己应用的 Service
2. **只影响第三方服务**：无法查询其他应用的服务（这是安全限制）
3. **我们的场景**：我们只查询自己的 RecordingService，完全没问题

### 替代方案

如果不想使用废弃的 API，可以考虑：

1. **使用 Binder**：通过 `bindService()` 连接到 Service
2. **使用 LiveData**：在 Service 中暴露 LiveData，ViewModel 订阅
3. **使用 SharedPreferences**：Service 写入状态，ViewModel 读取
4. **使用 Room Database**：持久化状态

但对于我们的简单场景，`getRunningServices()` 是最简单有效的方案。

## 测试步骤

### 1. 编译安装

```bash
cd /Users/nicholasmac/Documents/code/recorder
./build-and-install.sh
```

### 2. 测试流程

```bash
1. 打开应用
2. 点击"开始录制"
3. 看到录制状态和时长
4. 按 Home 键进入后台
5. 等待 5 秒
6. 重新打开应用
7. ✅ 应该看到"录制中"状态
8. ✅ 应该看到录制时长在增加
9. ✅ 应该可以点击"停止录制"
```

### 3. 验证日志

```bash
adb logcat -s RecorderViewModelSimple:D RecordingService:D
```

**应该看到的日志**：

```
RecorderViewModelSimple: Broadcast receivers registered
RecorderViewModelSimple: RecordingService is running, restoring state to RECORDING ✓
```

## 效果对比

### 修复前

| 操作 | Service 状态 | UI 显示 | 问题 |
|------|-------------|---------|------|
| 开始录制 | 运行中 | 录制中 ✓ | 正常 |
| 进入后台 | 运行中 | - | 正常 |
| 返回应用 | 运行中 | 待机 ❌ | **错误** |

### 修复后

| 操作 | Service 状态 | UI 显示 | 状态 |
|------|-------------|---------|------|
| 开始录制 | 运行中 | 录制中 ✓ | 正常 |
| 进入后台 | 运行中 | - | 正常 |
| 返回应用 | 运行中 | 录制中 ✓ | **正常** |

## 相关问题修复

这次修复还涉及到之前的修复：

1. **相机后台运行修复**（`ServiceLifecycleOwner`）
   - 确保相机在后台保持运行
   - 参考：[BACKGROUND_RECORDING_FIX.md](./BACKGROUND_RECORDING_FIX.md)

2. **UI 状态恢复修复**（本修复）
   - 确保返回应用时 UI 状态正确
   - 参考：本文档

这两个修复共同保证了完整的后台录制体验！

## 总结

✅ **相机在后台运行** - ServiceLifecycleOwner 修复  
✅ **UI 状态正确显示** - 本次修复  
✅ **完整的后台录制功能** - 两个修复配合

现在用户可以：
1. 开始录制
2. 切换到其他应用
3. 随时返回查看录制状态
4. 正确停止录制

**问题彻底解决！** 🎉

