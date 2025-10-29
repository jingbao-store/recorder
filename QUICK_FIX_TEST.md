# 🚀 快速修复测试指南

## 已修复的 Bug

### ✅ Bug #1: ANR (UI 冻结)
- **问题**：点击"开始录制"后，UI 冻结 5 秒
- **修复**：将初始化操作移到后台线程

### ✅ Bug #2: MediaCodec EOS 错误
- **问题**：录制时抛出 "EOS was already signaled" 异常
- **修复**：只在停止录制时发送一次结束信号

---

## 📦 安装测试

### 1. 连接设备并安装

```bash
# 检查设备
adb devices

# 安装应用
cd /Users/zhangrunsheng/Documents/GitHub/rokid/recorder
adb -s 1901092534000358 install -r app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb -s 1901092534000358 shell am start -n com.jingbao.recorder/.MainActivity
```

---

### 2. 监控日志（可选）

**新开一个终端**：
```bash
adb -s 1901092534000358 logcat -s RecordingService:D MediaEncoder:E GraphicBufferSource:E ActivityManager:E
```

---

## 🧪 测试步骤

### 测试 1：验证无 ANR

1. 打开应用
2. 授予权限
3. **点击"开始录制"**
4. **立即点击"后台运行"** ← 关键测试点

**预期**：
- ✅ UI 始终响应，无延迟
- ✅ 按钮点击立即生效
- ✅ 无"应用无响应"对话框

---

### 测试 2：验证无 EOS 错误

1. 开始录制
2. 等待 10 秒
3. 停止录制

**预期**：
- ✅ 日志中无 "EOS was already signaled" 错误
- ✅ 日志显示 "Video EOS signaled" **只出现一次**
- ✅ 视频文件正常生成

---

### 测试 3：检查视频文件

```bash
# 查看文件
adb -s 1901092534000358 shell ls -lh /sdcard/Movies/Camera/

# 导出到电脑
adb -s 1901092534000358 pull /sdcard/Movies/Camera/AR_Recording_*.mp4 .

# 播放验证
open AR_Recording_*.mp4
```

**预期**：
- ✅ 文件大小 > 0
- ✅ 视频可以播放
- ✅ 画中画效果正常

---

## ✅ 验收标准

### 必须通过

- [ ] 点击"开始录制"后 UI 不冻结
- [ ] 日志中无 ANR 错误
- [ ] 日志中无 EOS 错误
- [ ] 视频文件正常生成
- [ ] 视频可以播放

### 可选检查

- [ ] 录制启动时间 < 2 秒
- [ ] 后台录制正常工作
- [ ] 画中画位置正确（右下角）
- [ ] 导航流畅（D-pad/触摸板）

---

## 📝 正常日志示例

```
RecordingService: Service created
RecordingService: onStartCommand: action_start_recording
RecordingService: Starting recording in service
MediaEncoder: Initializing MediaEncoder
MediaEncoder: Video encoder started
MediaEncoder: Audio encoder started
MediaEncoder: MediaMuxer started
RecordingService: Recording started successfully in service

... (录制中) ...

RecordingService: Stopping recording in service
MediaEncoder: Stopping encoding
MediaEncoder: Video EOS signaled          ← 只出现一次
MediaEncoder: Audio EOS signaled          ← 只出现一次
RecordingService: Recording saved: /sdcard/Movies/Camera/AR_Recording_20251028_215900.mp4
```

---

## ❌ 不应出现的错误

### ANR 错误（已修复）
```
ActivityManager: ANR in com.jingbao.recorder
ActivityManager: Input dispatching timed out
```

### EOS 错误（已修复）
```
GraphicBufferSource: EOS was already signaled
MediaEncoder: Error signaling video frame
IllegalStateException at MediaCodec.signalEndOfInputStream
```

---

## 🎯 快速验证命令

**一键测试**：
```bash
# 安装、启动、监控日志
adb -s 1901092534000358 install -r app/build/outputs/apk/debug/app-debug.apk && \
adb -s 1901092534000358 shell am start -n com.jingbao.recorder/.MainActivity && \
adb -s 1901092534000358 logcat -s RecordingService:D MediaEncoder:E ActivityManager:E
```

**检查错误**：
```bash
# 只显示错误（应该为空）
adb -s 1901092534000358 logcat -s MediaEncoder:E GraphicBufferSource:E ActivityManager:E | grep -i "anr\|eos\|error"
```

---

## 💡 如果遇到问题

### 问题 1：设备未连接

```bash
# 检查 USB 连接
adb devices

# 如果为空，重启 adb
adb kill-server
adb start-server
```

---

### 问题 2：安装失败

```bash
# 卸载旧版本
adb -s 1901092534000358 uninstall com.jingbao.recorder

# 重新安装
adb -s 1901092534000358 install app/build/outputs/apk/debug/app-debug.apk
```

---

### 问题 3：权限未授予

```bash
# 手动授予所有权限
adb -s 1901092534000358 shell pm grant com.jingbao.recorder android.permission.CAMERA
adb -s 1901092534000358 shell pm grant com.jingbao.recorder android.permission.RECORD_AUDIO
```

---

## 📊 测试报告

完成测试后，填写：

**测试日期**：____________  
**设备型号**：1901092534000358 (Rokid AR Glasses)  
**Android 版本**：12 (API 32)

**测试结果**：

| 测试项 | 结果 | 备注 |
|--------|------|------|
| 无 ANR | ☐ 通过 ☐ 失败 | |
| 无 EOS 错误 | ☐ 通过 ☐ 失败 | |
| 视频生成 | ☐ 通过 ☐ 失败 | |
| 视频播放 | ☐ 通过 ☐ 失败 | |
| UI 响应 | ☐ 通过 ☐ 失败 | |

**总体评价**：☐ 优秀  ☐ 良好  ☐ 需要改进

---

**测试完成后，如果一切正常，这两个关键 Bug 就彻底解决了！** ✅

