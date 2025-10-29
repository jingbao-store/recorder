package com.jingbao.recorder.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodecList
import android.util.Log
import android.view.Surface
import com.jingbao.recorder.model.RecordingConfig
import java.io.File
import java.nio.ByteBuffer

/**
 * 媒体编码器
 * 使用 MediaCodec 编码视频和音频，MediaMuxer 合成 MP4 文件
 */
class MediaEncoder(
    private val outputFile: File,
    private val config: RecordingConfig
) {
    
    companion object {
        private const val TAG = "MediaEncoder"
        private const val MIME_TYPE_VIDEO_HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC
        private const val MIME_TYPE_VIDEO_AVC = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val MIME_TYPE_AUDIO = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val IFRAME_INTERVAL = 1 // I 帧间隔（秒）
        private const val TIMEOUT_US = 10000L // 10ms
    }
    
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private var videoEOSSent = false  // 防止重复发送 EOS
    private var audioEOSSent = false
    
    private var inputSurface: Surface? = null
    private var orientationHintDegrees: Int = 180 // 播放器层旋转 180°，修正整体倒置
    
    /**
     * 初始化编码器
     */
    fun init() {
        Log.d(TAG, "Initializing MediaEncoder: ${outputFile.absolutePath}")
        
        // 创建视频编码器
        initVideoEncoder()
        
        // 创建音频编码器
        initAudioEncoder()
        
        // 创建 MediaMuxer
        mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // 方向元数据（需在 start() 前设置）
        try {
            mediaMuxer?.setOrientationHint(orientationHintDegrees)
        } catch (_: Throwable) {}
        
        Log.d(TAG, "MediaEncoder initialized")
    }
    
    /**
     * 初始化视频编码器
     */
    private fun initVideoEncoder() {
        val chosenMime = chooseVideoMime()
        val format = MediaFormat.createVideoFormat(chosenMime, config.videoWidth, config.videoHeight)
        
        // 配置编码参数
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, config.videoFps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        // 显式设置色彩信息，贴近官方（BT.709 + LIMITED）
        try {
            format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
        } catch (_: Throwable) {}
        // 档位：HEVC Main 或 AVC High
        try {
            if (chosenMime == MIME_TYPE_VIDEO_HEVC) {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
            } else {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            }
        } catch (_: Throwable) {}
        // 码率模式 VBR
        try {
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        } catch (_: Throwable) {}
        
        Log.d(TAG, "Video format: ${config.videoWidth}x${config.videoHeight} @ ${config.videoFps}fps, bitrate: ${config.videoBitrate}, mime: $chosenMime")
        
        videoEncoder = MediaCodec.createEncoderByType(chosenMime)
        videoEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        
        // 获取输入 Surface
        inputSurface = videoEncoder?.createInputSurface()
        
        videoEncoder?.start()
        Log.d(TAG, "Video encoder started")
    }

    private fun chooseVideoMime(): String {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val infos = codecList.codecInfos
            val hevcSupported = infos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(MIME_TYPE_VIDEO_HEVC, ignoreCase = true) }
            }
            if (hevcSupported) MIME_TYPE_VIDEO_HEVC else MIME_TYPE_VIDEO_AVC
        } catch (_: Throwable) {
            MIME_TYPE_VIDEO_AVC
        }
    }
    
    /**
     * 初始化音频编码器
     */
    private fun initAudioEncoder() {
        val format = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, config.audioSampleRate, config.audioChannels)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrate)
        // 某些设备需要显式设置最大输入大小，避免输入缓冲区过小导致频繁溢出
        val bytesPerSecond = config.audioSampleRate * config.audioChannels * 2
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bytesPerSecond / 2)
        
        Log.d(TAG, "Audio format: ${config.audioSampleRate}Hz, ${config.audioChannels} channels, bitrate: ${config.audioBitrate}")
        
        audioEncoder = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO)
        audioEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioEncoder?.start()
        
        Log.d(TAG, "Audio encoder started")
    }
    
    /**
     * 获取视频输入 Surface
     */
    fun getInputSurface(): Surface? {
        return inputSurface
    }
    
    /**
     * 开始编码
     */
    fun start() {
        Log.d(TAG, "Starting encoding")
        muxerStarted = false
        videoTrackIndex = -1
        audioTrackIndex = -1
        videoEOSSent = false  // 重置标志
        audioEOSSent = false
    }
    
    /**
     * 编码音频数据
     * @param audioData 音频数据
     * @param presentationTimeUs 时间戳（微秒）
     */
    fun encodeAudioData(audioData: ByteBuffer, presentationTimeUs: Long) {
        val encoder = audioEncoder ?: return
        
        try {
            // 将源数据复制到编码器输入缓冲区（按块写入，避免溢出）
            val src = audioData.duplicate()
            // 将 position 复位到 0，limit 保持来表示有效数据长度
            src.position(0)
            var consumedBytes = 0
            val bytesPerSecond = config.audioSampleRate * config.audioChannels * 2 // 16-bit PCM
            while (src.hasRemaining()) {
                val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex < 0) {
                    break
                }
                val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                if (inputBuffer == null) {
                    encoder.releaseOutputBuffer(inputBufferIndex, false)
                    break
                }
                inputBuffer.clear()
                val bytesToWrite = minOf(src.remaining(), inputBuffer.remaining())
                // 临时限制 src，写入后恢复
                val oldLimit = src.limit()
                src.limit(src.position() + bytesToWrite)
                inputBuffer.put(src)
                src.limit(oldLimit)
                
                val chunkPtsUs = presentationTimeUs + if (bytesPerSecond > 0) {
                    (consumedBytes.toLong() * 1_000_000L) / bytesPerSecond
                } else 0L
                encoder.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    bytesToWrite,
                    chunkPtsUs,
                    0
                )
                consumedBytes += bytesToWrite
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding audio data", e)
        }
        
        // 输出编码数据
        drainEncoder(encoder, false)
    }
    
    /**
     * 通知视频帧可用（通过 Surface 输入时调用）
     * Surface 输入是异步的，编码器会自动处理帧，这里只需要提取编码后的数据
     */
    fun signalVideoFrameAvailable() {
        val encoder = videoEncoder ?: return
        
        try {
            // Surface 输入时，只需要提取编码后的数据
            // ❌ 不要调用 signalEndOfInputStream()！
            drainEncoder(encoder, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error draining video encoder", e)
        }
    }
    
    /**
     * 提取编码器输出数据
     */
    private fun drainEncoder(encoder: MediaCodec, endOfStream: Boolean) {
        val bufferInfo = MediaCodec.BufferInfo()
        
        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) {
                        break
                    }
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // 输出格式改变，添加轨道到 Muxer
                    val newFormat = encoder.outputFormat
                    Log.d(TAG, "Output format changed: $newFormat")
                    
                    synchronized(this) {
                        when (encoder) {
                            videoEncoder -> {
                                videoTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                                Log.d(TAG, "Video track added: $videoTrackIndex")
                            }
                            audioEncoder -> {
                                audioTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                                Log.d(TAG, "Audio track added: $audioTrackIndex")
                            }
                        }
                        
                        // 如果两个轨道都已添加，启动 Muxer
                        if (videoTrackIndex >= 0 && audioTrackIndex >= 0 && !muxerStarted) {
                            mediaMuxer?.start()
                            muxerStarted = true
                            Log.d(TAG, "MediaMuxer started")
                        }
                    }
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    
                    if (outputBuffer != null) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            // 配置数据，不写入文件
                            bufferInfo.size = 0
                        }
                        
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            
                            // 写入数据
                            synchronized(this) {
                                val trackIndex = when (encoder) {
                                    videoEncoder -> videoTrackIndex
                                    audioEncoder -> audioTrackIndex
                                    else -> -1
                                }
                                
                                if (trackIndex >= 0) {
                                    mediaMuxer?.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                }
                            }
                        }
                    }
                    
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        Log.d(TAG, "Reached end of stream")
                        break
                    }
                }
            }
        }
    }
    
    /**
     * 停止编码
     */
    fun stop() {
        Log.d(TAG, "Stopping encoding")
        
        try {
            // 停止视频编码器
            videoEncoder?.let { encoder ->
                try {
                    // ✅ 只发送一次结束信号
                    if (!videoEOSSent) {
                        encoder.signalEndOfInputStream()
                        videoEOSSent = true
                        Log.d(TAG, "Video EOS signaled")
                    }
                    drainEncoder(encoder, true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping video encoder", e)
                }
            }
            
            // 停止音频编码器（音频使用 Buffer 输入，需要手动标记结束）
            audioEncoder?.let { encoder ->
                try {
                    if (!audioEOSSent) {
                        val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inputBufferIndex >= 0) {
                            encoder.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            audioEOSSent = true
                            Log.d(TAG, "Audio EOS signaled")
                        }
                    }
                    drainEncoder(encoder, true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping audio encoder", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping encoders", e)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "Releasing MediaEncoder")
        
        try {
            videoEncoder?.stop()
            videoEncoder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing video encoder", e)
        }
        videoEncoder = null
        
        try {
            audioEncoder?.stop()
            audioEncoder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio encoder", e)
        }
        audioEncoder = null
        
        try {
            if (muxerStarted) {
                mediaMuxer?.stop()
            }
            mediaMuxer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing muxer", e)
        }
        mediaMuxer = null
        
        inputSurface?.release()
        inputSurface = null
        
        muxerStarted = false
        videoTrackIndex = -1
        audioTrackIndex = -1
    }
}

// 添加 AudioFormat 导入（如果需要）
private object AudioFormat {
    const val CHANNEL_IN_MONO = android.media.AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_IN_STEREO = android.media.AudioFormat.CHANNEL_IN_STEREO
}

