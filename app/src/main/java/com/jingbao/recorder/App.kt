package com.jingbao.recorder

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig

class App : Application(), CameraXConfig.Provider {
    override fun getCameraXConfig(): CameraXConfig {
        // 使用 Camera2 实现，并关闭 CameraX 的前置相机验证（只使用后置）
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
            .build()
    }
}


