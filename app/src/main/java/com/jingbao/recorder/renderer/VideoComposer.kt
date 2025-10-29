package com.jingbao.recorder.renderer

import android.graphics.SurfaceTexture
import android.opengl.*
import android.util.Log
import android.view.Surface
import com.jingbao.recorder.model.RecordingConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 视频合成器
 * 使用 OpenGL ES 将屏幕和摄像头画面合成为画中画效果
 */
class VideoComposer(private val config: RecordingConfig) {
    
    companion object {
        private const val TAG = "VideoComposer"
        
        // 顶点着色器（应用 SurfaceTexture 的纹理变换矩阵）
        private const val VERTEX_SHADER = """
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vec4 tex = uTexMatrix * vec4(aTextureCoord.xy, 0.0, 1.0);
                vTextureCoord = tex.xy;
            }
        """
        
        // 片段着色器 - 用于外部纹理（摄像头/屏幕）
        private const val FRAGMENT_SHADER_EXT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTextureCoord);
            }
        """
        
        // 全屏四边形顶点坐标
        private val FULL_RECTANGLE_COORDS = floatArrayOf(
            -1.0f, -1.0f,  // 左下
             1.0f, -1.0f,  // 右下
            -1.0f,  1.0f,  // 左上
             1.0f,  1.0f   // 右上
        )
        
        // 纹理坐标
        private val TEXTURE_COORDS = floatArrayOf(
            0.0f, 1.0f,  // 左下
            1.0f, 1.0f,  // 右下
            0.0f, 0.0f,  // 左上
            1.0f, 0.0f   // 右上
        )
    }
    
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var outputSurface: Surface? = null
    
    // OpenGL 程序和纹理
    private var shaderProgram = 0
    private var screenTextureId = 0
    private var cameraTextureId = 0
    private var screenSurfaceTexture: SurfaceTexture? = null
    private var cameraSurfaceTexture: SurfaceTexture? = null
    private val screenTexMatrix = FloatArray(16)
    private val cameraTexMatrix = FloatArray(16)
    
    // 顶点缓冲
    private var vertexBuffer: FloatBuffer? = null
    private var textureBuffer: FloatBuffer? = null
    // 是否启用屏幕输入（当 MediaProjection 不可用时关闭）
    private var screenInputEnabled: Boolean = true
    private var cameraInputWidth: Int = 0
    private var cameraInputHeight: Int = 0
    private var frameCount: Long = 0L
    private var frameDurationNs: Long = 0L
    
    /**
     * 初始化 EGL 和 OpenGL
     */
    fun init(outputSurface: Surface) {
        Log.d(TAG, "Initializing VideoComposer")
        this.outputSurface = outputSurface
        
        initEGL()
        initGL()
    }

    fun disableScreenInput() {
        Log.d(TAG, "Disabling screen input in VideoComposer")
        screenInputEnabled = false
    }
    
    /**
     * 初始化 EGL
     */
    private fun initEGL() {
        // 获取 EGL Display
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }
        
        // 初始化 EGL
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }
        
        // 配置 EGL
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)) {
            throw RuntimeException("Unable to find RGB888+recordable ES2 EGL config")
        }
        
        // 创建 EGL Context
        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Failed to create EGL context")
        }
        
        // 创建 Window Surface
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], outputSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL surface")
        }
        
        // 设置当前上下文
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("Failed to make EGL context current")
        }
        
        Log.d(TAG, "EGL initialized")
    }
    
    /**
     * 初始化 OpenGL
     */
    private fun initGL() {
        // 创建着色器程序
        shaderProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT)
        if (shaderProgram == 0) {
            throw RuntimeException("Failed to create shader program")
        }
        
        // 创建纹理
        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        screenTextureId = textures[0]
        cameraTextureId = textures[1]
        
        // 配置纹理
        for (textureId in textures) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        
        // 创建 SurfaceTexture
        screenSurfaceTexture = SurfaceTexture(screenTextureId)
        screenSurfaceTexture?.setDefaultBufferSize(config.videoWidth, config.videoHeight)
        
        cameraSurfaceTexture = SurfaceTexture(cameraTextureId)
        val cameraWidth = (config.videoWidth * config.pipWidthRatio).toInt()
        val cameraHeight = (config.videoHeight * config.pipHeightRatio).toInt()
        cameraInputWidth = cameraWidth
        cameraInputHeight = cameraHeight
        cameraSurfaceTexture?.setDefaultBufferSize(cameraInputWidth, cameraInputHeight)
        
        // 初始化顶点缓冲
        vertexBuffer = ByteBuffer.allocateDirect(FULL_RECTANGLE_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(FULL_RECTANGLE_COORDS)
        vertexBuffer?.position(0)
        
        textureBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(TEXTURE_COORDS)
        textureBuffer?.position(0)
        
        // 设置视口
        GLES20.glViewport(0, 0, config.videoWidth, config.videoHeight)
        
        // 启用混合（用于透明度）
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        // 严格 CFR 的时间戳（按帧序号计算）
        frameDurationNs = if (config.videoFps > 0) (1_000_000_000L / config.videoFps) else 33_333_333L
        frameCount = 0L
        
        Log.d(TAG, "OpenGL initialized")
    }
    
    /**
     * 创建着色器程序
     */
    private fun createProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Failed to link program: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return 0
        }
        
        return program
    }
    
    /**
     * 加载着色器
     */
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Failed to compile shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
    
    /**
     * 获取屏幕输入 Surface
     */
    fun getScreenSurface(): Surface {
        return Surface(screenSurfaceTexture)
    }
    
    /**
     * 获取摄像头输入 Surface
     */
    fun getCameraSurface(): Surface {
        return Surface(cameraSurfaceTexture)
    }

    fun resizeCameraInput(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == cameraInputWidth && height == cameraInputHeight) return
        cameraInputWidth = width
        cameraInputHeight = height
        cameraSurfaceTexture?.setDefaultBufferSize(width, height)
        Log.d(TAG, "Camera input resized to ${width}x${height}")
    }
    
    /**
     * 渲染一帧（合成屏幕和摄像头画面）
     */
    fun renderFrame(): Boolean {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            Log.e(TAG, "Failed to make EGL context current")
            return false
        }
        
        // 更新纹理
        if (screenInputEnabled) {
            screenSurfaceTexture?.updateTexImage()
            screenSurfaceTexture?.getTransformMatrix(screenTexMatrix)
        }
        cameraSurfaceTexture?.updateTexImage()
        cameraSurfaceTexture?.getTransformMatrix(cameraTexMatrix)
        
        // 清空画布
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        GLES20.glUseProgram(shaderProgram)
        
        // 1. 渲染屏幕（全屏），当屏幕输入不可用时仅使用清屏色
        if (screenInputEnabled) {
            drawTexture(screenTextureId, FULL_RECTANGLE_COORDS, screenTexMatrix)
        }
        
        // 2. 渲染摄像头
        val cameraCoords = if (screenInputEnabled) {
            // 画中画，右下角
            calculatePipCoordinates()
        } else {
            // 屏幕输入禁用时，摄像头全屏
            FULL_RECTANGLE_COORDS
        }
        drawTexture(cameraTextureId, cameraCoords, cameraTexMatrix)
        
        // 严格按帧序号的 CFR 时间戳
        val presentationTimeNs = frameCount * frameDurationNs
        frameCount += 1
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
        // 交换缓冲区
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        
        return true
    }
    
    /**
     * 计算画中画位置坐标（OpenGL 坐标系）
     */
    private fun calculatePipCoordinates(): FloatArray {
        val width = config.pipWidthRatio * 2.0f  // OpenGL 坐标系宽度为 2
        val height = config.pipHeightRatio * 2.0f
        val margin = config.pipMarginRatio * 2.0f
        
        // 右下角位置
        val right = 1.0f - margin
        val left = right - width
        val bottom = -1.0f + margin
        val top = bottom + height
        
        return floatArrayOf(
            left, bottom,   // 左下
            right, bottom,  // 右下
            left, top,      // 左上
            right, top      // 右上
        )
    }
    
    /**
     * 绘制纹理
     */
    private fun drawTexture(textureId: Int, vertexCoords: FloatArray, texMatrix: FloatArray) {
        // 更新顶点坐标
        val vb = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexCoords)
        vb.position(0)
        
        // 绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        
        // 设置顶点属性
        val positionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vb)
        
        val texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTextureCoord")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
        
        // 纹理矩阵（纠正旋转/镜像/裁切）
        val texMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexMatrix")
        GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, texMatrix, 0)

        // 设置纹理采样器
        val textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture")
        GLES20.glUniform1i(textureHandle, 0)
        
        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        // 清理
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "Releasing VideoComposer")
        
        // 释放 SurfaceTexture
        screenSurfaceTexture?.release()
        cameraSurfaceTexture?.release()
        
        // 释放 OpenGL 资源
        if (shaderProgram != 0) {
            GLES20.glDeleteProgram(shaderProgram)
            shaderProgram = 0
        }
        
        if (screenTextureId != 0 || cameraTextureId != 0) {
            GLES20.glDeleteTextures(2, intArrayOf(screenTextureId, cameraTextureId), 0)
            screenTextureId = 0
            cameraTextureId = 0
        }
        
        // 释放 EGL
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
            EGL14.eglTerminate(eglDisplay)
        }
        
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}

