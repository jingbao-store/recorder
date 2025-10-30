package com.jingbao.recorder.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jingbao.recorder.R
import com.jingbao.recorder.encoder.MediaEncoder
import com.jingbao.recorder.model.RecordingConfig
import com.jingbao.recorder.recorder.AudioRecorder
import com.jingbao.recorder.recorder.CameraRecorder
import com.jingbao.recorder.recorder.ScreenRecorder
import com.jingbao.recorder.renderer.VideoComposer
import com.jingbao.recorder.lifecycle.ServiceLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext
import java.io.File
import android.os.Environment
import java.text.SimpleDateFormat
import java.util.*

/**
 * 录制前台服务
 * 在后台独立管理录制流程，不依赖 Activity 生命周期
 */
class RecordingService : Service() {
    
    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording_channel"
        private const val CHANNEL_NAME = "录制服务"
        
        const val ACTION_START = "action_start_recording"
        const val ACTION_STOP = "action_stop_recording"
        const val ACTION_PAUSE = "action_pause_recording"
        const val ACTION_RESUME = "action_resume_recording"
        
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_CAMERA_ONLY = "extra_camera_only"
        
        const val BROADCAST_RECORDING_STATE = "com.jingbao.recorder.RECORDING_STATE"
        const val BROADCAST_RECORDING_DURATION = "com.jingbao.recorder.RECORDING_DURATION"
        const val BROADCAST_RECORDING_ERROR = "com.jingbao.recorder.RECORDING_ERROR"
        const val EXTRA_STATE = "extra_state"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        
        /**
         * 启动录制服务
         */
        fun startRecording(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startCameraOnly(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CAMERA_ONLY, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 停止录制服务
         */
        fun stopRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
    
    private val config = RecordingConfig()
    // ✅ 使用 Default dispatcher 避免阻塞主线程
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    
    // ✅ Service 专用的 LifecycleOwner，不受应用前后台切换影响
    private val serviceLifecycleOwner = ServiceLifecycleOwner()
    
    // 录制组件
    private var screenRecorder: ScreenRecorder? = null
    private var cameraRecorder: CameraRecorder? = null
    private var audioRecorder: AudioRecorder? = null
    private var videoComposer: VideoComposer? = null
    private var mediaEncoder: MediaEncoder? = null
    
    private var isRecording = false
    private var recordingStartTime = 0L
    private var renderJob: Job? = null
    private var timerJob: Job? = null
    private var outputFile: File? = null
    private var glDispatcher: CloseableCoroutineDispatcher? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        
        // ✅ 启动 Service 生命周期
        serviceLifecycleOwner.start()
        Log.d(TAG, "ServiceLifecycleOwner started, state: ${serviceLifecycleOwner.getCurrentState()}")
        
        // ✅ 延迟初始化录制组件，避免阻塞 onCreate
        screenRecorder = ScreenRecorder(this)
        cameraRecorder = CameraRecorder(this)
        // AudioRecorder 的 init 在后台线程调用
        audioRecorder = AudioRecorder(config.audioSampleRate, config.audioChannels)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                val cameraOnly = intent.getBooleanExtra(EXTRA_CAMERA_ONLY, false)
                if (cameraOnly) {
                    startRecordingInternal(Activity.RESULT_CANCELED, null)
                } else {
                    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                    
                    if (data != null) {
                        startRecordingInternal(resultCode, data)
                    } else {
                        Log.e(TAG, "MediaProjection data is null")
                        broadcastError("启动录制失败：缺少屏幕录制权限数据")
                        stopSelf()
                    }
                }
            }
            ACTION_STOP -> {
                stopRecordingInternal()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopRecordingInternal()
        
        // ✅ 停止 Service 生命周期
        serviceLifecycleOwner.stop()
        Log.d(TAG, "ServiceLifecycleOwner stopped")
        
        serviceScope.cancel()
        glDispatcher?.close()
        glDispatcher = null
    }
    
    /**
     * 启动录制（内部实现）
     */
    private fun startRecordingInternal(resultCode: Int, data: Intent?) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting recording in service")
                
                // 启动前台服务
                val notification = createNotification("正在录制...")
                startForeground(NOTIFICATION_ID, notification)
                
                // 创建输出文件
                outputFile = createOutputFile()
                
                // 先尝试初始化屏幕录制；若失败，则降级为仅摄像头+音频
                var screenInputEnabled = true
                if (data != null && resultCode == Activity.RESULT_OK) {
                    try {
                        screenRecorder?.init(resultCode, data)
                    } catch (e: Exception) {
                        Log.e(TAG, "ScreenRecorder init failed, will disable screen input", e)
                        screenInputEnabled = false
                    }
                } else {
                    Log.w(TAG, "Screen capture permission not provided, camera-only mode")
                    screenInputEnabled = false
                }
                
                // ✅ 初始化 AudioRecorder（在后台线程）
                audioRecorder?.init()
                
                // 初始化 MediaEncoder
                mediaEncoder = MediaEncoder(outputFile!!, config).apply {
                    init()
                    start()
                }
                
                // 初始化 VideoComposer（在单线程 GL 调度器上创建并绑定 EGL）
                val encoderSurface = mediaEncoder?.getInputSurface()
                if (encoderSurface == null) {
                    throw RuntimeException("Failed to get encoder surface")
                }
                if (glDispatcher == null) {
                    glDispatcher = newSingleThreadContext("GLRenderer")
                }
                withContext(glDispatcher!!) {
                    videoComposer = VideoComposer(config).apply {
                        init(encoderSurface)
                        if (!screenInputEnabled) {
                            disableScreenInput()
                        }
                    }
                }
                
                // 若屏幕输入可用，则启动屏幕捕获
                if (screenInputEnabled) {
                    screenRecorder?.startCapture(
                        videoComposer!!.getScreenSurface(),
                        config.videoWidth,
                        config.videoHeight
                    )
                }
                
                // 启动摄像头录制（在主线程）：在 CameraX 就绪回调中启动，避免 provider 未就绪
                handler.post {
                    // ✅ 使用 ServiceLifecycleOwner 替代 ProcessLifecycleOwner
                    // 这样相机不会因为应用进入后台而被释放
                    val cameraSurface = videoComposer!!.getCameraSurface()
                    val useFrontCamera = true
                    // 设置前置摄像头标志（用于镜像翻转修正）
                    glDispatcher?.let { dispatcher ->
                        serviceScope.launch(dispatcher) {
                            videoComposer?.setFrontCamera(useFrontCamera)
                        }
                    }
                    cameraRecorder?.init(serviceLifecycleOwner) {
                        Log.d(TAG, "Camera ready in service")
                        val reqW = if (screenInputEnabled) (config.videoWidth * config.pipWidthRatio).toInt() else config.videoWidth
                        val reqH = if (screenInputEnabled) (config.videoHeight * config.pipHeightRatio).toInt() else config.videoHeight
                        cameraRecorder?.startCapture(
                            serviceLifecycleOwner,
                            cameraSurface,
                            reqW,
                            reqH,
                            useFrontCamera = useFrontCamera
                        ) { agreedW, agreedH ->
                            // 同步摄像头输入 SurfaceTexture 的默认缓冲尺寸，避免 abandoned
                            glDispatcher?.let { dispatcher ->
                                serviceScope.launch(dispatcher) {
                                    videoComposer?.resizeCameraInput(agreedW, agreedH)
                                }
                            }
                        }
                    }
                }
                
                // 启动音频录制
                audioRecorder?.startRecording { audioData, presentationTimeUs ->
                    mediaEncoder?.encodeAudioData(audioData, presentationTimeUs)
                }
                
                // 更新状态
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                broadcastState("RECORDING")
                
                // 启动渲染循环（在 GL 单线程上）
                startRenderLoop()
                
                // 启动定时器更新通知
                startTimer()
                
                Log.d(TAG, "Recording started successfully in service")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording in service", e)
                broadcastError("启动录制失败: ${e.message}")
                stopSelf()
            }
        }
    }
    
    /**
     * 停止录制（内部实现）
     */
    private fun stopRecordingInternal() {
        // ✅ 在后台线程检查和处理停止逻辑
        serviceScope.launch {
            if (!isRecording) {
                stopSelf()
                return@launch
            }
            try {
                Log.d(TAG, "Stopping recording in service")
                
                // 停止定时器
                timerJob?.cancel()
                timerJob = null
                
                // 停止渲染循环
                renderJob?.cancel()
                renderJob = null
                
                // 停止所有录制
                audioRecorder?.stopRecording()
                withContext(Dispatchers.Main) {
                    cameraRecorder?.stopCapture()
                }
                screenRecorder?.stopCapture()
                
                // 停止编码器
                mediaEncoder?.stop()
                mediaEncoder?.release()
                mediaEncoder = null
                
                // 释放 VideoComposer（在 GL 单线程上）
                glDispatcher?.let { dispatcher ->
                    withContext(dispatcher) {
                        videoComposer?.release()
                        videoComposer = null
                    }
                } ?: run {
                    videoComposer?.release()
                    videoComposer = null
                }
                
                // 更新状态
                isRecording = false
                broadcastState("STOPPED")
                
                val duration = System.currentTimeMillis() - recordingStartTime
                val file = outputFile
                
                if (file != null && file.exists()) {
                    Log.d(TAG, "Recording saved: ${file.absolutePath}, duration: ${duration}ms")
                    // 可以通过广播通知录制完成
                } else {
                    broadcastError("录制文件未生成")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording in service", e)
                broadcastError("停止录制失败: ${e.message}")
            } finally {
                // 停止前台服务
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }
    
    /**
     * 启动渲染循环
     */
    private fun startRenderLoop() {
        val dispatcher = glDispatcher
        if (dispatcher == null) {
            Log.e(TAG, "GL dispatcher not initialized; cannot start render loop")
            return
        }
        renderJob = serviceScope.launch(dispatcher) {
            val frameIntervalMs = 1000L / config.videoFps
            
            while (isActive && isRecording) {
                val frameStartTime = System.currentTimeMillis()
                
                // 渲染一帧
                videoComposer?.renderFrame()
                
                // 通知编码器提取数据（Surface 输入是异步的，编码器自动处理帧）
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
    
    /**
     * 启动定时器更新通知
     */
    private fun startTimer() {
        timerJob = serviceScope.launch {
            while (isActive && isRecording) {
                val duration = System.currentTimeMillis() - recordingStartTime
                broadcastDuration(duration)
                updateNotification(formatDuration(duration))
                delay(1000) // 每秒更新一次
            }
        }
    }
    
    /**
     * 创建输出文件
     * 存储在 DIRECTORY_MOVIES/Camera 目录下，与 photoView4rokidglasses 兼容
     */
    private fun createOutputFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "AR_Recording_$timestamp.mp4"
        
        // 使用 DIRECTORY_MOVIES/Camera，与相机录制内容存储在同一位置
        val cameraDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "Camera"
        )
        
        if (!cameraDir.exists()) {
            cameraDir.mkdirs()
        }
        
        val file = File(cameraDir, fileName)
        Log.d(TAG, "Output file: ${file.absolutePath}")
        return file
    }
    
    /**
     * 格式化时长
     */
    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        
        return if (hours > 0) {
            String.format("正在录制... %02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("正在录制... %02d:%02d", minutes, seconds)
        }
    }
    
    /**
     * 广播录制状态
     */
    private fun broadcastState(state: String) {
        val intent = Intent(BROADCAST_RECORDING_STATE).apply {
            putExtra(EXTRA_STATE, state)
        }
        sendBroadcast(intent)
    }
    
    /**
     * 广播录制时长
     */
    private fun broadcastDuration(duration: Long) {
        val intent = Intent(BROADCAST_RECORDING_DURATION).apply {
            putExtra(EXTRA_DURATION, duration)
        }
        sendBroadcast(intent)
    }
    
    /**
     * 广播错误信息
     */
    private fun broadcastError(message: String) {
        val intent = Intent(BROADCAST_RECORDING_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, message)
        }
        sendBroadcast(intent)
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于显示录制状态"
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Notification channel created")
        }
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(contentText: String): Notification {
        // 点击通知打开应用
        val pendingIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        
        // 停止录制按钮
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AR 录制器")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "停止录制",
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

