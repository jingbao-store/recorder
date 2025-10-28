package com.jingbao.recorder.recorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.nio.ByteBuffer

/**
 * 音频录制器
 * 使用 AudioRecord 录制麦克风音频
 */
class AudioRecorder(
    private val sampleRate: Int = 44100,
    private val channels: Int = 1 // 1=单声道, 2=立体声
) {
    
    companion object {
        private const val TAG = "AudioRecorder"
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var onAudioDataCallback: ((ByteBuffer, Long) -> Unit)? = null
    
    private val channelConfig = if (channels == 1) {
        AudioFormat.CHANNEL_IN_MONO
    } else {
        AudioFormat.CHANNEL_IN_STEREO
    }
    
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    /**
     * 计算缓冲区大小
     */
    private fun getBufferSize(): Int {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        
        // 使用 2 倍的最小缓冲区大小以确保稳定性
        return minBufferSize * 2
    }
    
    /**
     * 初始化音频录制器
     */
    fun init() {
        val bufferSize = getBufferSize()
        
        Log.d(TAG, "Initializing audio recorder: sampleRate=$sampleRate, channels=$channels, bufferSize=$bufferSize")
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
            } else {
                Log.d(TAG, "AudioRecord initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            audioRecord = null
        }
    }
    
    /**
     * 开始录制音频
     * @param onAudioData 音频数据回调 (data, presentationTimeUs)
     */
    fun startRecording(onAudioData: (ByteBuffer, Long) -> Unit) {
        if (audioRecord == null) {
            Log.e(TAG, "AudioRecord not initialized")
            return
        }
        
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }
        
        onAudioDataCallback = onAudioData
        isRecording = true
        
        Log.d(TAG, "Starting audio recording")
        
        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            isRecording = false
            return
        }
        
        // 在后台线程读取音频数据
        recordingThread = Thread {
            recordAudioData()
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }
    
    /**
     * 录制音频数据循环
     */
    private fun recordAudioData() {
        val bufferSize = getBufferSize()
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        val startTime = System.nanoTime()
        
        Log.d(TAG, "Audio recording thread started")
        
        while (isRecording) {
            buffer.clear()
            
            val read = try {
                audioRecord?.read(buffer, bufferSize) ?: -1
            } catch (e: Exception) {
                Log.e(TAG, "Error reading audio data", e)
                break
            }
            
            if (read > 0) {
                buffer.limit(read)
                
                // 计算时间戳（微秒）
                val presentationTimeUs = (System.nanoTime() - startTime) / 1000
                
                // 回调音频数据
                onAudioDataCallback?.invoke(buffer, presentationTimeUs)
            } else if (read < 0) {
                Log.e(TAG, "Audio read error: $read")
                break
            }
        }
        
        Log.d(TAG, "Audio recording thread stopped")
    }
    
    /**
     * 停止录制
     */
    fun stopRecording() {
        if (!isRecording) {
            return
        }
        
        Log.d(TAG, "Stopping audio recording")
        isRecording = false
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
        }
        
        recordingThread?.join(1000)
        recordingThread = null
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "Releasing audio recorder")
        stopRecording()
        
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        
        audioRecord = null
        onAudioDataCallback = null
    }
    
    /**
     * 获取音频采样率
     */
    fun getSampleRate(): Int = sampleRate
    
    /**
     * 获取声道数
     */
    fun getChannelCount(): Int = channels
}

