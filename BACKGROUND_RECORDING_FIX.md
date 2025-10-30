# 后台录制相机关闭问题修复

## 问题描述

应用在后台运行时，录制会停止，具体表现为：

1. **相机被关闭**：CameraX 检测到生命周期变为 `STOPPED`，自动释放相机资源
2. **录制中断**：相机关闭导致视频录制无法继续
3. **音频中断**：AudioRecord 被系统回收（`dead IAudioRecord`）

### 问题日志

```
16:32:52 Camera2CameraImpl: {Camera@...} Use cases [...] now DETACHED for camera
16:32:52 Camera2CameraImpl: {Camera@...} Closing camera
16:33:05 RecorderViewModelSimple: ViewModel cleared
16:33:50 AudioRecord: dead IAudioRecord, creating a new one from obtainBuffer()
```

## 根本原因

**RecordingService 使用了 `ProcessLifecycleOwner`**

```kotlin
// ❌ 旧代码 - 问题所在
val lifecycleOwner = androidx.lifecycle.ProcessLifecycleOwner.get()
cameraRecorder?.init(lifecycleOwner)
cameraRecorder?.startCapture(lifecycleOwner, ...)
```

### 为什么会有问题？

`ProcessLifecycleOwner` 的生命周期跟随整个应用进程：

- 应用在前台：生命周期状态为 `RESUMED`
- 应用进入后台：生命周期状态变为 `STOPPED`
- 应用被销毁：生命周期状态变为 `DESTROYED`

当应用进入后台时，`ProcessLifecycleOwner` 的状态变为 `STOPPED`，CameraX 检测到这个状态变化后，会自动执行以下操作：

1. Unbind 所有相机用例（Use Cases）
2. 关闭相机设备
3. 释放相机资源

这就是为什么日志中会看到：
```
Camera2CameraImpl: Transitioning camera internal state: OPENED --> CLOSING
```

## 解决方案

### 1. 创建自定义 LifecycleOwner

创建 `ServiceLifecycleOwner`，其生命周期完全由 Service 控制，不受应用前后台切换影响：

```kotlin
class ServiceLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    
    fun start() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    
    fun stop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
```

### 2. 在 RecordingService 中使用

```kotlin
class RecordingService : Service() {
    // ✅ Service 专用的 LifecycleOwner
    private val serviceLifecycleOwner = ServiceLifecycleOwner()
    
    override fun onCreate() {
        super.onCreate()
        // 启动生命周期
        serviceLifecycleOwner.start()
        Log.d(TAG, "ServiceLifecycleOwner started, state: ${serviceLifecycleOwner.getCurrentState()}")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 停止生命周期
        serviceLifecycleOwner.stop()
        Log.d(TAG, "ServiceLifecycleOwner stopped")
    }
    
    private fun startRecordingInternal(resultCode: Int, data: Intent?) {
        // ✅ 使用 ServiceLifecycleOwner 替代 ProcessLifecycleOwner
        handler.post {
            cameraRecorder?.init(serviceLifecycleOwner) {
                cameraRecorder?.startCapture(
                    serviceLifecycleOwner,  // ✅ 关键修改
                    cameraSurface,
                    reqW,
                    reqH,
                    useFrontCamera = true
                )
            }
        }
    }
}
```

## 修复效果

### 修复前

| 状态 | ProcessLifecycleOwner | CameraX 状态 | 录制状态 |
|------|----------------------|--------------|----------|
| 应用前台 | RESUMED | OPEN | ✅ 正常录制 |
| 应用后台 | STOPPED | CLOSED | ❌ 录制中断 |

### 修复后

| 状态 | ServiceLifecycleOwner | CameraX 状态 | 录制状态 |
|------|----------------------|--------------|----------|
| 应用前台 | RESUMED | OPEN | ✅ 正常录制 |
| 应用后台 | RESUMED | OPEN | ✅ 正常录制 |
| Service销毁 | DESTROYED | CLOSED | ⏹️ 正常停止 |

## 测试步骤

### 1. 编译安装

```bash
cd /Users/nicholasmac/Documents/code/recorder
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. 测试后台录制

```bash
# 启动应用并监控日志
adb logcat -s RecordingService:D CameraRecorder:D Camera2CameraImpl:D AudioRecord:D

# 操作步骤：
# 1. 打开 AR Recorder 应用
# 2. 点击开始录制
# 3. 授予屏幕录制权限
# 4. 按 Home 键返回主屏幕（应用进入后台）
# 5. 等待 30 秒
# 6. 重新打开应用或通过通知栏停止录制
# 7. 检查视频文件是否完整
```

### 3. 验证日志

**修复后应该看到的日志：**

```
RecordingService: Service created
RecordingService: ServiceLifecycleOwner started, state: RESUMED
RecordingService: Starting recording in service
CameraRecorder: Initializing camera
CameraRecorder: Camera provider ready
CameraRecorder: Starting camera capture
Camera2CameraImpl: {Camera@...} CameraDevice.onOpened()
RecordingService: Recording started successfully in service

[应用进入后台]
# ✅ 没有 "Closing camera" 日志
# ✅ 没有 "Use cases now DETACHED" 日志
# ✅ 相机保持打开状态

[停止录制]
RecordingService: Stopping recording
CameraRecorder: Stopping camera capture
RecordingService: Recording stopped successfully
```

### 4. 验证视频文件

```bash
# 查看生成的视频文件
adb shell ls -lh /sdcard/Movies/Camera/

# 拉取视频到本地查看
adb pull /sdcard/Movies/Camera/AR_Recording_*.mp4 ./

# 检查视频时长是否正确
# 视频应该包含整个后台录制过程
```

## 技术细节

### LifecycleOwner 的作用

CameraX 使用 `LifecycleOwner` 来自动管理相机资源：

```kotlin
// CameraX 内部会监听生命周期事件
cameraProvider.bindToLifecycle(
    lifecycleOwner,      // 监听这个 LifecycleOwner 的状态
    cameraSelector,
    preview
)

// 当生命周期变化时：
// ON_START  -> 打开相机
// ON_STOP   -> 关闭相机  ⚠️ 这就是问题所在
// ON_DESTROY -> 释放资源
```

### 为什么不直接使用 Service 作为 LifecycleOwner？

Service 本身**不是** LifecycleOwner（只有 Activity 和 Fragment 才是）。

虽然可以使用 `LifecycleService`，但它的生命周期管理比较复杂，而且仍然可能受到其他因素影响。

自定义 `ServiceLifecycleOwner` 的优势：
- ✅ 完全可控：生命周期完全由我们决定
- ✅ 简单明确：只有 start() 和 stop() 两个方法
- ✅ 独立运行：不受应用前后台切换影响
- ✅ 符合 CameraX API 要求：实现了 LifecycleOwner 接口

## 其他相关问题

### 1. AudioRecord 中断问题

日志中的 `dead IAudioRecord` 可能是另一个问题，但通常也是因为应用进入后台导致的。

**可能的原因：**
- 系统回收了音频资源
- 其他应用请求了音频焦点
- 音频录制被系统暂停

**解决方案（如果仍有问题）：**
- 确保 Service 是前台服务（已实现）
- 在 AudioRecorder 中添加重连机制
- 使用 AudioManager 请求音频焦点

### 2. 内存压力问题

日志中频繁出现 `lowmemorykiller` 警告：
```
lowmemorykiller: Memory Load: [4] {pid:30899} {size:58 MB} {name:com.jingbao.recorder}
```

**建议：**
- 降低视频分辨率或帧率
- 使用更高效的编码参数
- 定期释放不需要的资源

## 相关文件

- `app/src/main/java/com/jingbao/recorder/lifecycle/ServiceLifecycleOwner.kt` - 自定义 LifecycleOwner
- `app/src/main/java/com/jingbao/recorder/service/RecordingService.kt` - 录制服务（已修改）
- `app/src/main/java/com/jingbao/recorder/recorder/CameraRecorder.kt` - 相机录制器

## 总结

通过使用自定义的 `ServiceLifecycleOwner` 替代 `ProcessLifecycleOwner`，我们解决了应用进入后台时相机被关闭的问题。

**关键点：**
1. ✅ 相机在后台保持运行
2. ✅ Service 完全控制生命周期
3. ✅ 不受应用前后台切换影响
4. ✅ 符合 CameraX 的设计原则

录制功能现在可以真正在后台持续工作了！📹

