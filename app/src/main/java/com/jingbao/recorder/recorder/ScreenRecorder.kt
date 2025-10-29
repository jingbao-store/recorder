package com.jingbao.recorder.recorder

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager

/**
 * 屏幕录制器
 * 使用 MediaProjection API 捕获屏幕内容
 */
class ScreenRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenRecorder"
        private const val VIRTUAL_DISPLAY_NAME = "ARRecorder"
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    
    /**
     * 初始化屏幕录制
     * @param resultCode 权限请求返回的 resultCode
     * @param data 权限请求返回的 Intent
     */
    fun init(resultCode: Int, data: Intent) {
        Log.d(TAG, "Initializing screen recorder")
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection not available (permission data invalid)")
            throw IllegalStateException("MediaProjection not initialized")
        }
        
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped")
                release()
            }
        }, null)
    }
    
    /**
     * 开始捕获屏幕到指定的 Surface
     * @param surface 输出 Surface
     * @param width 视频宽度
     * @param height 视频高度
     */
    fun startCapture(surface: Surface, width: Int, height: Int) {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection not initialized")
            return
        }
        
        val metrics = getScreenMetrics()
        val density = metrics.densityDpi
        
        Log.d(TAG, "Starting screen capture: ${width}x${height}, density: $density")
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )
    }
    
    /**
     * 停止捕获
     */
    fun stopCapture() {
        Log.d(TAG, "Stopping screen capture")
        virtualDisplay?.release()
        virtualDisplay = null
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "Releasing screen recorder")
        stopCapture()
        mediaProjection?.stop()
        mediaProjection = null
    }
    
    /**
     * 获取屏幕指标
     */
    private fun getScreenMetrics(): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }
    
    /**
     * 获取屏幕尺寸
     */
    fun getScreenSize(): Pair<Int, Int> {
        val metrics = getScreenMetrics()
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }
}

