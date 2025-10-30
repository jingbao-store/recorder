#!/bin/bash

# 检查后台录制状态脚本

DEVICE_SERIAL="1901092534000358"

echo "========================================"
echo "  后台录制状态检查"
echo "========================================"
echo ""

# 检查 Service 是否运行
echo "1. 检查 RecordingService 状态:"
SERVICE_STATUS=$(adb -s $DEVICE_SERIAL shell "dumpsys activity services com.jingbao.recorder" | grep -A 5 "RecordingService")
if [ -n "$SERVICE_STATUS" ]; then
    echo "✓ Service 正在运行"
else
    echo "✗ Service 未运行"
fi
echo ""

# 检查相机状态
echo "2. 检查相机状态（最近 50 行日志）:"
echo ""
echo "--- ServiceLifecycleOwner 状态 ---"
adb -s $DEVICE_SERIAL logcat -d -t 50 | grep "ServiceLifecycleOwner" | tail -3
echo ""

echo "--- 相机打开状态 ---"
CAMERA_OPEN=$(adb -s $DEVICE_SERIAL logcat -d -t 100 | grep "CameraDevice.onOpened" | tail -1)
if [ -n "$CAMERA_OPEN" ]; then
    echo "✓ 相机已打开: $CAMERA_OPEN"
else
    echo "⚠ 未找到相机打开日志"
fi
echo ""

echo "--- 相机关闭状态 ---"
CAMERA_CLOSE=$(adb -s $DEVICE_SERIAL logcat -d -t 100 | grep "Closing camera" | tail -1)
if [ -n "$CAMERA_CLOSE" ]; then
    echo "✗ 相机已关闭: $CAMERA_CLOSE"
else
    echo "✓ 未发现相机关闭日志（相机应该还在运行）"
fi
echo ""

echo "--- 相机分离状态 ---"
CAMERA_DETACHED=$(adb -s $DEVICE_SERIAL logcat -d -t 100 | grep "DETACHED" | tail -1)
if [ -n "$CAMERA_DETACHED" ]; then
    echo "✗ 相机用例已分离: $CAMERA_DETACHED"
else
    echo "✓ 未发现相机分离日志"
fi
echo ""

# 检查录制时长
echo "3. 检查录制时长:"
DURATION=$(adb -s $DEVICE_SERIAL logcat -d | grep "duration:" | tail -1)
if [ -n "$DURATION" ]; then
    echo "$DURATION"
else
    echo "⚠ 未找到时长信息"
fi
echo ""

# 检查视频文件
echo "4. 检查视频文件:"
VIDEO_FILE=$(adb -s $DEVICE_SERIAL shell "ls -t /sdcard/Movies/Camera/AR_Recording_*.mp4 2>/dev/null" | head -1 | tr -d '\r')
if [ -n "$VIDEO_FILE" ]; then
    FILE_SIZE=$(adb -s $DEVICE_SERIAL shell "ls -lh '$VIDEO_FILE'" | awk '{print $4}')
    FILE_TIME=$(adb -s $DEVICE_SERIAL shell "ls -l '$VIDEO_FILE'" | awk '{print $6, $7}')
    echo "✓ 最新视频: $VIDEO_FILE"
    echo "  大小: $FILE_SIZE"
    echo "  时间: $FILE_TIME"
    
    # 实时监控文件大小变化（判断是否还在录制）
    echo ""
    echo "  监控文件大小变化（3秒）..."
    SIZE1=$(adb -s $DEVICE_SERIAL shell "stat -c%s '$VIDEO_FILE'" 2>/dev/null | tr -d '\r')
    sleep 3
    SIZE2=$(adb -s $DEVICE_SERIAL shell "stat -c%s '$VIDEO_FILE'" 2>/dev/null | tr -d '\r')
    
    if [ "$SIZE1" != "$SIZE2" ]; then
        DIFF=$((SIZE2 - SIZE1))
        echo "  ✓ 文件大小增加了 $DIFF 字节 - 录制仍在进行！"
    else
        echo "  ✗ 文件大小未变化 - 录制可能已停止"
    fi
else
    echo "✗ 未找到视频文件"
fi
echo ""

# 检查音频录制
echo "5. 检查音频录制状态:"
AUDIO_STATUS=$(adb -s $DEVICE_SERIAL logcat -d -t 50 | grep "AudioRecorder" | tail -3)
if [ -n "$AUDIO_STATUS" ]; then
    echo "$AUDIO_STATUS"
else
    echo "⚠ 未找到音频录制日志"
fi
echo ""

# 显示最近的错误
echo "6. 检查最近的错误:"
ERRORS=$(adb -s $DEVICE_SERIAL logcat -d -t 100 | grep -E "com.jingbao.recorder.*E " | tail -5)
if [ -n "$ERRORS" ]; then
    echo "$ERRORS"
else
    echo "✓ 未发现错误"
fi
echo ""

echo "========================================"
echo "  实时监控（Ctrl+C 停止）"
echo "========================================"
echo ""
adb -s $DEVICE_SERIAL logcat -s RecordingService:D CameraRecorder:D Camera2CameraImpl:D AudioRecorder:D

