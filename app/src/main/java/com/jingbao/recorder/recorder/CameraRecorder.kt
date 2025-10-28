package com.jingbao.recorder.recorder

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

/**
 * 摄像头录制器
 * 使用 CameraX 捕获摄像头画面
 */
class CameraRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraRecorder"
    }
    
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var currentSurface: Surface? = null
    private val executor: Executor = ContextCompat.getMainExecutor(context)
    
    /**
     * 初始化摄像头
     * @param lifecycleOwner Activity/Fragment 的 LifecycleOwner
     * @param onCameraReady 摄像头就绪回调
     */
    fun init(lifecycleOwner: LifecycleOwner, onCameraReady: () -> Unit = {}) {
        Log.d(TAG, "Initializing camera")
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture?.addListener({
            try {
                cameraProvider = cameraProviderFuture?.get()
                Log.d(TAG, "Camera provider ready")
                onCameraReady()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera", e)
            }
        }, executor)
    }
    
    /**
     * 开始捕获摄像头到指定的 Surface
     * @param lifecycleOwner Activity/Fragment 的 LifecycleOwner
     * @param surface 输出 Surface
     * @param width 视频宽度
     * @param height 视频高度
     * @param useFrontCamera 是否使用前置摄像头（默认为 true）
     */
    fun startCapture(
        lifecycleOwner: LifecycleOwner,
        surface: Surface,
        width: Int,
        height: Int,
        useFrontCamera: Boolean = true
    ) {
        val provider = cameraProvider
        if (provider == null) {
            Log.e(TAG, "Camera provider not initialized")
            return
        }
        
        currentSurface = surface
        
        Log.d(TAG, "Starting camera capture: ${width}x${height}, front camera: $useFrontCamera")
        
        // 选择摄像头
        val cameraSelector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        // 创建预览用例
        preview = Preview.Builder()
            .setTargetResolution(Size(width, height))
            .build()
        
        preview?.setSurfaceProvider { request: SurfaceRequest ->
            Log.d(TAG, "Providing surface to camera: ${request.resolution}")
            request.provideSurface(surface, executor) { result ->
                Log.d(TAG, "Surface usage complete: $result")
            }
        }
        
        try {
            // 解绑所有用例
            provider.unbindAll()
            
            // 绑定到生命周期
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
            
            Log.d(TAG, "Camera bound to lifecycle")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera", e)
        }
    }
    
    /**
     * 停止捕获
     */
    fun stopCapture() {
        Log.d(TAG, "Stopping camera capture")
        cameraProvider?.unbindAll()
        currentSurface = null
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "Releasing camera recorder")
        stopCapture()
        cameraProvider = null
        cameraProviderFuture = null
    }
    
    /**
     * 检查设备是否有前置摄像头
     */
    fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }
    
    /**
     * 检查设备是否有后置摄像头
     */
    fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }
}

