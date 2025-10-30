package com.jingbao.recorder.viewmodel

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jingbao.recorder.model.RecordingState
import com.jingbao.recorder.service.RecordingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 简化的录制 ViewModel
 * 只负责与 Service 通信和 UI 状态管理
 */
class RecorderViewModelSimple : ViewModel() {
    
    companion object {
        private const val TAG = "RecorderViewModelSimple"
    }
    
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()
    
    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _shouldMinimizeApp = MutableStateFlow(false)
    val shouldMinimizeApp: StateFlow<Boolean> = _shouldMinimizeApp.asStateFlow()
    
    // MediaProjection 相关
    private var mediaProjectionData: Intent? = null
    private var mediaProjectionResultCode = Activity.RESULT_CANCELED
    
    // 广播接收器
    private var stateReceiver: BroadcastReceiver? = null
    private var durationReceiver: BroadcastReceiver? = null
    private var errorReceiver: BroadcastReceiver? = null
    
    /**
     * 注册广播接收器
     */
    fun registerReceivers(context: Context) {
        // 状态接收器
        stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getStringExtra(RecordingService.EXTRA_STATE)
                Log.d(TAG, "Received state: $state")
                _recordingState.value = when (state) {
                    "RECORDING" -> RecordingState.RECORDING
                    "STOPPED" -> RecordingState.STOPPED
                    else -> RecordingState.IDLE
                }
            }
        }
        context.registerReceiver(
            stateReceiver,
            IntentFilter(RecordingService.BROADCAST_RECORDING_STATE),
            Context.RECEIVER_NOT_EXPORTED
        )
        
        // 时长接收器
        durationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val duration = intent.getLongExtra(RecordingService.EXTRA_DURATION, 0L)
                _recordingDuration.value = duration
            }
        }
        context.registerReceiver(
            durationReceiver,
            IntentFilter(RecordingService.BROADCAST_RECORDING_DURATION),
            Context.RECEIVER_NOT_EXPORTED
        )
        
        // 错误接收器
        errorReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val error = intent.getStringExtra(RecordingService.EXTRA_ERROR_MESSAGE)
                Log.e(TAG, "Received error: $error")
                _errorMessage.value = error
            }
        }
        context.registerReceiver(
            errorReceiver,
            IntentFilter(RecordingService.BROADCAST_RECORDING_ERROR),
            Context.RECEIVER_NOT_EXPORTED
        )
        
        Log.d(TAG, "Broadcast receivers registered")
        
        // ✅ 检查 Service 是否正在运行，如果是则恢复状态
        if (isRecordingServiceRunning(context)) {
            Log.d(TAG, "RecordingService is running, restoring state to RECORDING")
            _recordingState.value = RecordingState.RECORDING
        }
    }
    
    /**
     * 检查 RecordingService 是否正在运行
     */
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
    
    /**
     * 注销广播接收器
     */
    fun unregisterReceivers(context: Context) {
        try {
            stateReceiver?.let { context.unregisterReceiver(it) }
            durationReceiver?.let { context.unregisterReceiver(it) }
            errorReceiver?.let { context.unregisterReceiver(it) }
            Log.d(TAG, "Broadcast receivers unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers", e)
        }
    }
    
    /**
     * 更新权限状态
     */
    fun updatePermissions(hasPermissions: Boolean) {
        _hasPermissions.value = hasPermissions
    }
    
    /**
     * 请求屏幕录制权限
     */
    fun requestScreenCapture(context: Context, launcher: ActivityResultLauncher<Intent>) {
        try {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = projectionManager.createScreenCaptureIntent()
            
            // 检查 Intent 是否可以被解析
            val packageManager = context.packageManager
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            
            if (resolveInfo != null) {
                launcher.launch(intent)
            } else {
                // 在某些定制设备上（如 Rokid），系统可能没有标准的权限 Activity，
                // 但实际允许直接使用 MediaProjection。尝试直接走授权回调。
                try {
                    Log.w(TAG, "MediaProjection permission activity not found; trying direct grant path")
                    onScreenCaptureResult(context, Activity.RESULT_OK, intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Direct grant path failed; falling back to camera-only", e)
                    _errorMessage.value = "设备不支持屏幕录制授权，已降级为仅摄像头录制"
                    RecordingService.startCameraOnly(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request screen capture permission", e)
            _errorMessage.value = "无法请求屏幕录制权限，已降级为仅摄像头录制"
        }
    }
    
    /**
     * 处理屏幕录制权限结果
     */
    fun onScreenCaptureResult(context: Context, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            mediaProjectionResultCode = resultCode
            mediaProjectionData = data
            Log.d(TAG, "Screen capture permission granted, starting service")
            
            // 立即启动录制服务
            startRecording(context)
        } else {
            _errorMessage.value = "需要屏幕录制权限才能使用此功能"
            Log.e(TAG, "Screen capture permission denied")
        }
    }
    
    /**
     * 开始录制
     */
    private fun startRecording(context: Context) {
        viewModelScope.launch {
            try {
                val data = mediaProjectionData
                if (data == null) {
                    _errorMessage.value = "屏幕录制权限数据丢失"
                    return@launch
                }
                
                Log.d(TAG, "Starting recording service")
                
                // 启动录制服务
                RecordingService.startRecording(context, mediaProjectionResultCode, data)
                
                // 设置标志以最小化应用
                _shouldMinimizeApp.value = true
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                _errorMessage.value = "启动录制失败: ${e.message}"
            }
        }
    }
    
    /**
     * 停止录制
     */
    fun stopRecording(context: Context) {
        Log.d(TAG, "Stopping recording service")
        RecordingService.stopRecording(context)
        _recordingState.value = RecordingState.IDLE
        _recordingDuration.value = 0L
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * 重置最小化标志
     */
    fun resetMinimizeFlag() {
        _shouldMinimizeApp.value = false
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}

