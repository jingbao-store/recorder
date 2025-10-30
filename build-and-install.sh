#!/bin/bash

# 快速编译和安装脚本

set -e

DEVICE_SERIAL="1901092534000358"
PACKAGE_NAME="com.jingbao.recorder"

echo "========================================"
echo "  快速编译和安装"
echo "========================================"
echo ""

# 设置 JAVA_HOME
if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    echo "✓ 使用 Android Studio 自带的 JBR: $JAVA_HOME"
else
    echo "⚠️  未找到 Android Studio JBR，使用系统默认 Java"
fi
echo ""

# 检查设备
echo "检查设备连接..."
if ! adb -s $DEVICE_SERIAL get-state > /dev/null 2>&1; then
    echo "❌ 设备 $DEVICE_SERIAL 未连接"
    echo ""
    echo "可用设备："
    adb devices
    exit 1
fi
echo "✓ 设备已连接"
echo ""

# 编译
echo "编译应用..."
./gradlew assembleDebug
echo "✓ 编译完成"
echo ""

# 安装
echo "安装应用..."
adb -s $DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
echo "✓ 安装完成"
echo ""

# 启动应用
echo "启动应用..."
adb -s $DEVICE_SERIAL shell am start -n $PACKAGE_NAME/.MainActivity
echo "✓ 应用已启动"
echo ""

# 监控日志
echo "========================================"
echo "监控日志（Ctrl+C 停止）"
echo "========================================"
echo ""
adb -s $DEVICE_SERIAL logcat -s RecordingService:D CameraRecorder:D Camera2CameraImpl:D AudioRecorder:D ServiceLifecycleOwner:D

