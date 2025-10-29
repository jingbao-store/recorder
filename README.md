# AR 画中画录制器

专为 Rokid AR 眼镜设计的录像应用，支持同时录制屏幕内容和摄像头画面，实时合成为画中画效果的视频。

## 功能特点

✅ **实时画中画合成** - 屏幕录制和摄像头录制同步进行，摄像头画面显示在右下角  
✅ **后台录制** - 录制开始后自动最小化，可录制其他应用操作  
✅ **通知栏控制** - 通过通知栏实时查看状态和停止录制  
✅ **音频录制** - 录制麦克风音频  
✅ **硬件加速** - 使用 MediaCodec 硬件编码，OpenGL ES 实时渲染  
✅ **独立服务** - Service 独立运行，不依赖 Activity 生命周期  
✅ **Material 3 UI** - 现代化的 Jetpack Compose 界面  
✅ **权限管理** - 完整的权限请求流程  

## 技术架构

### 核心技术栈
- **Kotlin** - 主开发语言
- **Jetpack Compose** - 现代化 UI 框架
- **CameraX** - 摄像头管理
- **MediaProjection** - 屏幕录制
- **MediaCodec** - 视频/音频编码
- **MediaMuxer** - 音视频封装
- **OpenGL ES 2.0** - 实时视频合成
- **Coroutines** - 异步处理

### 数据流程

```
屏幕 (MediaProjection) ──→ Surface ──┐
                                   ├──→ OpenGL合成 ──→ MediaCodec ──→ MediaMuxer ──→ MP4文件
摄像头 (CameraX) ──→ Surface ──────┘                        ↑
麦克风 (AudioRecord) ───────────────────────────────────────┘
```

### 项目结构

```
app/src/main/java/com/jingbao/recorder/
├── model/                      # 数据模型
│   └── RecordingState.kt      # 录制状态、配置、结果
├── recorder/                   # 录制核心模块
│   ├── ScreenRecorder.kt      # 屏幕录制（MediaProjection）
│   ├── CameraRecorder.kt      # 摄像头录制（CameraX）
│   └── AudioRecorder.kt       # 音频录制（AudioRecord）
├── renderer/                   # 渲染模块
│   └── VideoComposer.kt       # OpenGL 视频合成器
├── encoder/                    # 编码模块
│   └── MediaEncoder.kt        # 媒体编码器（MediaCodec + MediaMuxer）
├── service/                    # 服务
│   └── RecordingService.kt    # 前台服务
├── viewmodel/                  # ViewModel
│   └── RecorderViewModel.kt   # 录制业务逻辑
├── ui/                         # UI 界面
│   ├── RecorderScreen.kt      # 主界面
│   └── theme/                 # 主题
└── MainActivity.kt             # 主 Activity
```

## 系统要求

- **Android 12+** (API Level 31+)
- **摄像头权限**
- **麦克风权限**
- **屏幕录制权限**
- **通知权限** (Android 13+)

## 使用说明

### 安装
1. 克隆项目
```bash
git clone <repository-url>
cd recorder
```

2. 在 Android Studio 中打开项目

3. 连接 Rokid 设备
```bash
adb devices
```

4. 构建并安装
```bash
./gradlew installDebug
```

### 使用流程

1. **授予权限** - 首次运行时，应用会请求相机、麦克风和通知权限
2. **开始录制** - 点击红色圆形按钮开始录制，授予屏幕录制权限
3. **自动后台** - 录制开始后，应用自动最小化到后台
4. **录制其他应用** - 返回主屏幕，打开任何应用，所有操作都会被录制
5. **停止录制** - 通过通知栏点击「停止录制」按钮，或重新打开应用点击停止
6. **查看视频** - 录制的视频保存在 `/Movies/Camera/` 目录下（与 photoView4rokidglasses 兼容）

### 🌟 后台录制特性

- **自动最小化**：录制开始后应用自动进入后台
- **通知栏控制**：实时显示录制时长，随时停止录制
- **独立运行**：不受应用切换影响，稳定录制
- **完整记录**：可以录制游戏、应用操作、AR 体验等任何内容

详细说明请查看：[BACKGROUND_RECORDING.md](BACKGROUND_RECORDING.md)

### 🕶️ Rokid 眼镜专属优化

- **480x640 适配**：完美适配 Rokid 小屏幕
- **触摸板导航**：支持方向键焦点导航
- **设计规范**：遵循 Rokid 官方设计规范
- **性能优化**：降低分辨率和码率，流畅运行

详细说明请查看：[ROKID_OPTIMIZATION.md](ROKID_OPTIMIZATION.md)

## 配置参数

在 `RecordingConfig` 中可以调整录制参数：

```kotlin
data class RecordingConfig(
    val videoWidth: Int = 1920,           // 视频宽度
    val videoHeight: Int = 1080,          // 视频高度
    val videoFps: Int = 30,               // 帧率
    val videoBitrate: Int = 8_000_000,    // 视频码率 (8 Mbps)
    val audioSampleRate: Int = 44100,     // 音频采样率
    val audioBitrate: Int = 128_000,      // 音频码率 (128 kbps)
    val audioChannels: Int = 1,           // 声道数（单声道）
    
    // 画中画配置
    val pipWidthRatio: Float = 0.25f,     // 摄像头窗口宽度比例
    val pipHeightRatio: Float = 0.25f,    // 摄像头窗口高度比例
    val pipMarginRatio: Float = 0.02f,    // 边距比例
    val pipCornerRadius: Float = 16f      // 圆角半径
)
```

## 性能优化建议

对于 Rokid 设备，推荐以下配置以获得最佳性能：

- 分辨率：1920x1080 或 1280x720
- 帧率：30fps（较低性能设备可降至 24fps）
- 视频码率：6-8 Mbps
- 使用硬件编码器（默认）

## 已知问题

1. ⚠️ 部分 API 使用了已弃用的方法（有警告但不影响使用）
2. ⚠️ 暂停功能尚未实现
3. ⚠️ 需要在实际设备上测试性能

## 待优化项

- [ ] 在 Rokid 设备上进行实际测试
- [ ] 根据设备性能动态调整参数
- [ ] 添加暂停/恢复功能
- [ ] 支持自定义画中画位置
- [ ] 添加录制预览功能
- [ ] 优化电量消耗
- [ ] 添加录制质量选项

## 许可证

本项目仅供学习和开发使用。

## 开发者

开发日期：2025-10-28  
目标设备：Rokid RG-glasses (Android 12, API 32)

