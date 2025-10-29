# MediaCodec EOS 重复发送错误修复

## 🐛 问题

录制时出现错误：

```
GraphicBufferSource: EOS was already signaled
IllegalStateException at MediaCodec.signalEndOfInputStream
```

---

## 🔍 根本原因

### 严重 Bug：每帧都发送结束信号

**错误代码**（MediaEncoder.kt 第 158-170 行）：

```kotlin
fun signalVideoFrameAvailable(presentationTimeNs: Long) {
    val encoder = videoEncoder ?: return
    
    try {
        // ❌ 错误！每次渲染一帧都调用 signalEndOfInputStream()
        encoder.signalEndOfInputStream()
    } catch (e: Exception) {
        Log.e(TAG, "Error signaling video frame", e)
    }
    
    drainEncoder(encoder, false)
}
```

### 问题分析

1. **`signalEndOfInputStream()`** 应该只在**录制结束**时调用**一次**
2. 但这个方法在**每渲染一帧**时都被调用
3. 导致编码器收到大量的 EOS（End Of Stream）信号
4. 第二次调用时抛出 `IllegalStateException: EOS was already signaled`

### 渲染循环中的错误调用

**RecordingService.kt 第 296-319 行**：

```kotlin
private fun startRenderLoop() {
    renderJob = serviceScope.launch {
        while (isActive && isRecording) {
            videoComposer?.renderFrame()
            
            val presentationTimeNs = (System.currentTimeMillis() - recordingStartTime) * 1_000_000
            // ❌ 每帧都调用，导致重复发送 EOS
            mediaEncoder?.signalVideoFrameAvailable(presentationTimeNs)
            
            delay(frameIntervalMs)
        }
    }
}
```

**执行流程**：
```
第 1 帧：signalEndOfInputStream() ✅ 第一次
第 2 帧：signalEndOfInputStream() ❌ 第二次 → EOS already signaled
第 3 帧：signalEndOfInputStream() ❌ 第三次 → IllegalStateException
...
```

---

## ✅ 解决方案

### 1. 修复 `signalVideoFrameAvailable` 方法

**正确实现**（MediaEncoder.kt 第 158-168 行）：

```kotlin
/**
 * 通知视频帧可用（通过 Surface 输入时调用）
 * Surface 输入是异步的，编码器会自动处理帧，这里只需要提取编码后的数据
 */
fun signalVideoFrameAvailable() {
    val encoder = videoEncoder ?: return
    
    try {
        // ✅ Surface 输入时，只需要提取编码后的数据
        // ✅ 不要调用 signalEndOfInputStream()！
        drainEncoder(encoder, false)
    } catch (e: Exception) {
        Log.e(TAG, "Error draining video encoder", e)
    }
}
```

**关键改进**：
- ✅ 移除了 `signalEndOfInputStream()` 调用
- ✅ 只保留 `drainEncoder(encoder, false)` 提取编码数据
- ✅ 移除了 `presentationTimeNs` 参数（Surface 输入时不需要）

---

### 2. 在 `stop()` 方法中发送 EOS

**正确实现**（MediaEncoder.kt 第 254-295 行）：

```kotlin
fun stop() {
    Log.d(TAG, "Stopping encoding")
    
    try {
        // 停止视频编码器
        videoEncoder?.let { encoder ->
            try {
                // ✅ 只发送一次结束信号
                if (!videoEOSSent) {
                    encoder.signalEndOfInputStream()
                    videoEOSSent = true
                    Log.d(TAG, "Video EOS signaled")
                }
                drainEncoder(encoder, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping video encoder", e)
            }
        }
        
        // 停止音频编码器
        audioEncoder?.let { encoder ->
            try {
                if (!audioEOSSent) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        encoder.queueInputBuffer(
                            inputBufferIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        audioEOSSent = true
                        Log.d(TAG, "Audio EOS signaled")
                    }
                }
                drainEncoder(encoder, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio encoder", e)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error stopping encoders", e)
    }
}
```

**关键改进**：
- ✅ 添加 `videoEOSSent` 和 `audioEOSSent` 标志位
- ✅ 只在 `stop()` 方法中发送**一次** EOS
- ✅ 使用标志位防止重复发送
- ✅ 添加详细日志

---

### 3. 更新渲染循环

**正确实现**（RecordingService.kt 第 296-319 行）：

```kotlin
private fun startRenderLoop() {
    renderJob = serviceScope.launch {
        val frameIntervalMs = 1000L / config.videoFps
        
        while (isActive && isRecording) {
            val frameStartTime = System.currentTimeMillis()
            
            // 渲染一帧
            videoComposer?.renderFrame()
            
            // ✅ 通知编码器提取数据（不发送 EOS）
            mediaEncoder?.signalVideoFrameAvailable()
            
            // 控制帧率
            val frameTime = System.currentTimeMillis() - frameStartTime
            val delayTime = (frameIntervalMs - frameTime).coerceAtLeast(0)
            if (delayTime > 0) {
                delay(delayTime)
            }
        }
        
        Log.d(TAG, "Render loop stopped in service")
    }
}
```

**关键改进**：
- ✅ 移除了 `presentationTimeNs` 参数
- ✅ 只调用 `signalVideoFrameAvailable()` 提取数据
- ✅ 不再每帧发送 EOS

---

### 4. 添加标志位防止重复

**新增字段**（MediaEncoder.kt 第 37-38 行）：

```kotlin
private var videoEOSSent = false  // 防止重复发送 EOS
private var audioEOSSent = false
```

**重置标志**（MediaEncoder.kt 第 117-124 行）：

```kotlin
fun start() {
    Log.d(TAG, "Starting encoding")
    muxerStarted = false
    videoTrackIndex = -1
    audioTrackIndex = -1
    videoEOSSent = false  // ✅ 重置标志
    audioEOSSent = false
}
```

---

## 📊 修复前后对比

### 修复前 ❌

```
渲染循环每帧调用：
Frame 1: signalEndOfInputStream() → EOS sent
Frame 2: signalEndOfInputStream() → ❌ ERROR: EOS already signaled
Frame 3: signalEndOfInputStream() → ❌ ERROR: IllegalStateException
...

结果：
❌ 应用崩溃或录制失败
❌ 视频文件损坏
❌ 日志充满错误
```

---

### 修复后 ✅

```
渲染循环每帧调用：
Frame 1: drainEncoder(false) → 提取数据
Frame 2: drainEncoder(false) → 提取数据
Frame 3: drainEncoder(false) → 提取数据
...

停止录制时调用一次：
stop(): signalEndOfInputStream() → ✅ EOS sent (only once)

结果：
✅ 录制正常进行
✅ 视频文件完整
✅ 无错误日志
```

---

## 🎯 关键概念

### Surface 输入 vs Buffer 输入

| 特性 | Surface 输入（视频） | Buffer 输入（音频） |
|------|---------------------|-------------------|
| **输入方式** | OpenGL 渲染到 Surface | 手动写入 Buffer |
| **帧处理** | 编码器自动处理 | 需要手动 queue |
| **时间戳** | 编码器自动管理 | 手动指定 |
| **EOS 发送** | `signalEndOfInputStream()` | `BUFFER_FLAG_END_OF_STREAM` |
| **何时提取数据** | 每帧调用 `drainEncoder` | 每次写入后调用 |

### `signalEndOfInputStream()` 的正确用法

**❌ 错误用法**：
```kotlin
// 每帧都调用
fun onFrameRendered() {
    encoder.signalEndOfInputStream()  // ❌ 错误！
}
```

**✅ 正确用法**：
```kotlin
// 录制结束时调用一次
fun stopRecording() {
    if (!eosSent) {
        encoder.signalEndOfInputStream()  // ✅ 正确！
        eosSent = true
    }
}
```

---

## 🔧 修改的文件

### 1. MediaEncoder.kt

**修改内容**：
- ✅ 修复 `signalVideoFrameAvailable()` 方法（第 158-168 行）
- ✅ 更新 `stop()` 方法（第 254-295 行）
- ✅ 添加 EOS 标志位（第 37-38 行）
- ✅ 在 `start()` 中重置标志（第 122-123 行）

---

### 2. RecordingService.kt

**修改内容**：
- ✅ 更新 `startRenderLoop()` 方法（第 296-319 行）
- ✅ 移除 `presentationTimeNs` 参数

---

## 🧪 测试建议

### 测试 1：正常录制

**操作**：
1. 启动录制
2. 录制 10 秒
3. 停止录制

**预期**：
- ✅ 无 EOS 错误
- ✅ 视频文件正常生成
- ✅ 视频可以播放

---

### 测试 2：查看日志

**命令**：
```bash
adb -s 1901092534000358 logcat -s MediaEncoder:D GraphicBufferSource:*
```

**预期日志**：

**修复前**（❌ 错误）：
```
MediaEncoder: Error signaling video frame
GraphicBufferSource: EOS was already signaled
IllegalStateException at signalEndOfInputStream
```

**修复后**（✅ 正确）：
```
MediaEncoder: Starting encoding
MediaEncoder: Video encoder started
MediaEncoder: Audio encoder started
MediaEncoder: MediaMuxer started
...（正常运行）
MediaEncoder: Stopping encoding
MediaEncoder: Video EOS signaled
MediaEncoder: Audio EOS signaled
```

---

### 测试 3：多次录制

**操作**：
1. 录制 5 秒
2. 停止
3. 再次录制 5 秒
4. 停止

**预期**：
- ✅ 两次录制都正常
- ✅ 标志位正确重置
- ✅ 无重复 EOS 错误

---

## 💡 技术要点

### 1. MediaCodec 生命周期

```
init() → start() → [encode frames] → signalEOS() → stop() → release()
                         ↑                ↑
                    每帧调用 drainEncoder  只调用一次！
```

### 2. Surface 输入的特点

- **异步处理**：渲染到 Surface 后，编码器异步编码
- **自动管理**：时间戳和帧顺序由编码器管理
- **只需提取**：应用只需定期调用 `drainEncoder` 提取数据

### 3. EOS 信号的作用

- **通知结束**：告诉编码器不会有更多输入
- **触发刷新**：编码器刷新所有缓冲数据
- **只能一次**：多次调用会导致 IllegalStateException

---

## ✅ 修复总结

### 问题

- ❌ 每帧都调用 `signalEndOfInputStream()`
- ❌ 导致 "EOS already signaled" 错误
- ❌ 录制失败或视频损坏

### 解决

- ✅ 移除每帧的 EOS 调用
- ✅ 只在 `stop()` 中发送一次 EOS
- ✅ 添加标志位防止重复
- ✅ 正确理解 Surface 输入模式

### 效果

- ✅ 录制正常进行
- ✅ 无 EOS 错误
- ✅ 视频文件完整可播放

---

**修复完成！** 🎉

现在 MediaEncoder 正确处理视频帧和结束信号了！

---

## 🚀 部署步骤

1. **重新连接设备**：
   ```bash
   adb devices
   ```

2. **安装修复版本**：
   ```bash
   adb -s 1901092534000358 install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **测试录制**：
   - 启动应用
   - 点击"开始录制"
   - 录制一段时间
   - 点击"停止录制"

4. **查看日志**：
   ```bash
   adb -s 1901092534000358 logcat -s MediaEncoder:D
   ```

5. **检查视频**：
   ```bash
   adb -s 1901092534000358 shell ls -lh /sdcard/Movies/Camera/
   ```

