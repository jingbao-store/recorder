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
 * 默认参数尽量对齐官方视频（可按设备能力调整）
 */
data class RecordingConfig(
    // 视频配置 - 对齐官方：2400x1800@30fps，~11 Mbps（设备不支持时可下调）
    val videoWidth: Int = 2400,
    val videoHeight: Int = 1800,
    val videoFps: Int = 30,
    val videoBitrate: Int = 11_000_000,
    
    // 音频配置 - 对齐官方：16 kHz, 96 kbps, 单声道
    val audioSampleRate: Int = 16000,
    val audioBitrate: Int = 96_000,
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

