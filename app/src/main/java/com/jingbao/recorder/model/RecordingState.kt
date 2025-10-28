package com.jingbao.recorder.model

/**
 * 录制状态
 */
enum class RecordingState {
    IDLE,           // 待机
    RECORDING,      // 录制中
    PAUSED,         // 暂停
    STOPPED         // 已停止
}

/**
 * 录制配置
 * 针对 Rokid 眼镜优化（480x640 屏幕）
 */
data class RecordingConfig(
    // 视频配置 - 适配 Rokid 小屏幕
    val videoWidth: Int = 480,         // Rokid 屏幕宽度
    val videoHeight: Int = 640,        // Rokid 屏幕高度
    val videoFps: Int = 30,
    val videoBitrate: Int = 4_000_000, // 4 Mbps（降低以适配性能）
    
    // 音频配置
    val audioSampleRate: Int = 44100,
    val audioBitrate: Int = 128_000,   // 128 kbps
    val audioChannels: Int = 1,        // 单声道
    
    // 画中画配置（右下角）
    val pipWidthRatio: Float = 0.30f,  // 摄像头窗口宽度 30%（更大更清晰）
    val pipHeightRatio: Float = 0.25f, // 摄像头窗口高度 25%
    val pipMarginRatio: Float = 0.03f, // 边距 3%
    val pipCornerRadius: Float = 12f   // 圆角 12px（符合 Rokid 设计规范）
)

/**
 * 录制结果
 */
sealed class RecordingResult {
    data class Success(val filePath: String, val durationMs: Long) : RecordingResult()
    data class Error(val message: String, val exception: Throwable? = null) : RecordingResult()
}

