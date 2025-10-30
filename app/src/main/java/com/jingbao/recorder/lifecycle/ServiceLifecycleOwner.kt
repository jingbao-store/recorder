package com.jingbao.recorder.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * Service 专用的 LifecycleOwner
 * 
 * 与 ProcessLifecycleOwner 不同，这个 LifecycleOwner 的生命周期完全由 Service 控制，
 * 不会因为应用进入后台而变成 STOPPED 状态，从而保证 CameraX 在后台持续运行。
 */
class ServiceLifecycleOwner : LifecycleOwner {
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    
    /**
     * 启动生命周期（Service onCreate/onStartCommand 时调用）
     */
    fun start() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    
    /**
     * 停止生命周期（Service onDestroy 时调用）
     */
    fun stop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
    
    /**
     * 获取当前生命周期状态
     */
    fun getCurrentState(): Lifecycle.State {
        return lifecycleRegistry.currentState
    }
}

