# AR 录制器 - 测试与安装指南

## APK 位置

Debug APK 已生成在：
```
app/build/outputs/apk/debug/app-debug.apk
```

## 安装到 Rokid 设备

### 方法 1：通过 ADB 安装（推荐）

1. 连接 Rokid 设备到电脑
```bash
adb devices
```

2. 确认设备已连接（应该看到 1901092534000358）
```
List of devices attached
1901092534000358        device
```

3. 安装 APK
```bash
cd /Users/zhangrunsheng/Documents/GitHub/rokid/recorder
adb -s 1901092534000358 install -r app/build/outputs/apk/debug/app-debug.apk
```

或者使用 Gradle：
```bash
./gradlew installDebug
```

### 方法 2：手动传输安装

1. 将 APK 传输到设备
```bash
adb -s 1901092534000358 push app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/
```

2. 在设备上使用文件管理器找到并安装 APK

## 测试清单

### ✅ 基础功能测试

1. **启动应用**
   - [ ] 应用能正常启动
   - [ ] UI 界面正常显示

2. **权限请求**
   - [ ] 点击"授予权限"按钮
   - [ ] 摄像头权限请求显示
   - [ ] 麦克风权限请求显示
   - [ ] 通知权限请求显示（Android 13+）
   - [ ] 所有权限授予后，界面显示录制按钮

3. **屏幕录制权限**
   - [ ] 点击红色圆形录制按钮
   - [ ] 系统弹出屏幕录制权限请求
   - [ ] 选择"现在开始"或"允许"

4. **录制功能**
   - [ ] 录制开始后，界面显示红点和计时器
   - [ ] 通知栏显示"正在录制..."通知
   - [ ] 摄像头画面应该在右下角显示（实际录制到视频中）
   - [ ] 计时器正常计数

5. **停止录制**
   - [ ] 点击白色方形停止按钮
   - [ ] 录制停止，计时器归零
   - [ ] 通知消失

6. **视频文件**
   - [ ] 检查视频是否生成
   ```bash
   adb -s 1901092534000358 shell ls -lh /sdcard/Movies/Camera/
   ```
   - [ ] 拉取视频到电脑查看
   ```bash
   adb -s 1901092534000358 pull /sdcard/Movies/Camera/AR_Recording_*.mp4 ./recordings/
   ```
   - [ ] 播放视频，检查：
     - 屏幕内容是否正常录制
     - 摄像头画面是否在右下角
     - 音频是否正常
     - 画中画比例是否合适

### ⚙️ 性能测试

1. **录制性能**
   - [ ] 录制过程是否流畅（30fps）
   - [ ] 设备是否过热
   - [ ] 电量消耗是否正常
   - [ ] 长时间录制（5分钟+）是否稳定

2. **资源使用**
   ```bash
   # 查看 CPU 使用率
   adb -s 1901092534000358 shell top -n 1 | grep com.jingbao.recorder
   
   # 查看内存使用
   adb -s 1901092534000358 shell dumpsys meminfo com.jingbao.recorder
   ```

3. **视频质量**
   - [ ] 1080p 是否流畅
   - [ ] 如果卡顿，尝试降低到 720p
   - [ ] 码率是否合适（8Mbps）

### 🐛 问题排查

#### 日志查看
实时查看应用日志：
```bash
adb -s 1901092534000358 logcat -s RecorderViewModel:D ScreenRecorder:D CameraRecorder:D AudioRecorder:D VideoComposer:D MediaEncoder:D RecordingService:D
```

清空日志后重新测试：
```bash
adb -s 1901092534000358 logcat -c
adb -s 1901092534000358 logcat | tee test.log
```

#### 常见问题

**问题 1：摄像头无法启动**
- 检查是否授予了摄像头权限
- 查看日志中的 CameraRecorder 错误信息
- Rokid 只有一个摄像头，确认代码中使用的是正确的摄像头

**问题 2：屏幕录制失败**
- 检查是否授予了屏幕录制权限
- 查看 ScreenRecorder 日志
- 确认 MediaProjection 初始化成功

**问题 3：视频合成失败**
- 检查 OpenGL 初始化日志
- 确认 EGL 上下文创建成功
- 查看 VideoComposer 错误信息

**问题 4：音频无声**
- 检查麦克风权限
- 确认 AudioRecord 初始化成功
- 检查音频编码器日志

**问题 5：应用崩溃**
- 查看完整的 crash log：
```bash
adb -s 1901092534000358 logcat *:E
```

**问题 6：录制卡顿**
- 降低分辨率（在 RecordingConfig 中修改）
- 降低帧率到 24fps
- 降低码率到 6Mbps

## 性能优化建议

### 根据实际测试结果调整参数

#### 如果性能良好（流畅无卡顿）
保持默认配置或提升质量：
```kotlin
videoWidth = 1920
videoHeight = 1080
videoFps = 30
videoBitrate = 8_000_000  // 8 Mbps
```

#### 如果轻微卡顿
```kotlin
videoWidth = 1280
videoHeight = 720
videoFps = 30
videoBitrate = 6_000_000  // 6 Mbps
```

#### 如果严重卡顿
```kotlin
videoWidth = 1280
videoHeight = 720
videoFps = 24
videoBitrate = 4_000_000  // 4 Mbps
```

### 修改配置位置
编辑文件：`app/src/main/java/com/jingbao/recorder/model/RecordingState.kt`

修改 `RecordingConfig` 类的默认值，然后重新构建并安装。

## 视频示例检查

播放生成的视频，应该看到：
1. **主画面**：完整的屏幕录制内容（你在 Rokid 上看到的内容）
2. **画中画**：右下角显示摄像头拍摄的画面（约 1/4 屏幕大小）
3. **音频**：麦克风录制的声音
4. **流畅度**：30fps 的流畅播放

## 下一步优化

测试完成后，根据实际效果进行以下优化：

1. **调整画中画位置和大小**
   - 修改 `RecordingConfig` 中的 `pipWidthRatio`、`pipHeightRatio`、`pipMarginRatio`

2. **优化画质**
   - 调整 `videoBitrate` 和 `videoFps`

3. **添加画中画边框和阴影**
   - 在 `VideoComposer.kt` 中的 shader 代码添加效果

4. **支持切换摄像头**
   - 如果 Rokid 有前后摄像头，添加切换功能

5. **添加暂停功能**
   - 在 ViewModel 中实现暂停/恢复逻辑

## 反馈

测试后请记录：
- ✅ 哪些功能正常工作
- ❌ 遇到的问题和错误信息
- 📊 性能表现（帧率、发热、电量）
- 💡 改进建议

这样可以帮助进一步优化应用！

