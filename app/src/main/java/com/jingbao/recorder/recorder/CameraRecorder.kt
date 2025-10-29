package com.jingbao.recorder.recorder

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.Surface
import android.os.Looper
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
        useFrontCamera: Boolean = true,
        onResolutionAgreed: ((Int, Int) -> Unit)? = null
    ) {
        val provider = cameraProvider
        if (provider == null) {
            Log.e(TAG, "Camera provider not initialized")
            return
        }
        
        currentSurface = surface
        
        Log.d(TAG, "Starting camera capture: ${width}x${height}, front camera: $useFrontCamera")
        
        // 选择摄像头（优先前置，若不可用则回退后置；反之亦然）
        val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        if (!hasFront && !hasBack) {
            Log.e(TAG, "No available camera can be found on this device")
            return
        }
        val cameraSelector = when {
            useFrontCamera && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
            !useFrontCamera && hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
            // 回退逻辑
            useFrontCamera && !hasFront && hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
            !useFrontCamera && !hasBack && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        // 创建预览用例
        preview = Preview.Builder()
            .setTargetResolution(Size(width, height))
            .build()
        
        preview?.setSurfaceProvider { request: SurfaceRequest ->
            Log.d(TAG, "Providing surface to camera: ${request.resolution}")
            // 通知上层采用 CameraX 的实际分辨率
            onResolutionAgreed?.invoke(request.resolution.width, request.resolution.height)
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
        // CameraX 要求在主线程调用 unbindAll
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cameraProvider?.unbindAll()
        } else {
            ContextCompat.getMainExecutor(context).execute {
                try {
                    cameraProvider?.unbindAll()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unbind camera on main thread", e)
                }
            }
        }
        try {
            currentSurface?.release()
        } catch (_: Throwable) {
        } finally {
            currentSurface = null
        }
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

