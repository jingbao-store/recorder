#!/bin/bash

# 快速测试脚本 - 编译、安装、启动应用并监控日志
# 需要手动点击录制按钮

set -e

DEVICE_SERIAL="1901092534000358"
PACKAGE_NAME="com.jingbao.recorder"

echo "=========================================="
echo "  快速测试 - 后台录制"
echo "=========================================="
echo ""

# 设置 JAVA_HOME
if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    echo "✓ 使用 Android Studio JBR"
else
    echo "⚠️  使用系统默认 Java"
fi
echo ""

# 检查设备
echo "1. 检查设备..."
if ! adb -s $DEVICE_SERIAL get-state > /dev/null 2>&1; then
    echo "❌ 设备未连接: $DEVICE_SERIAL"
    exit 1
fi
echo "✓ 设备已连接"
echo ""

# 编译
echo "2. 编译应用..."
./gradlew assembleDebug > /dev/null 2>&1
echo "✓ 编译完成"
echo ""

# 安装
echo "3. 安装应用..."
adb -s $DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk > /dev/null 2>&1
echo "✓ 安装完成"
echo ""

# 清空日志
adb -s $DEVICE_SERIAL logcat -c

# 启动应用
echo "4. 启动应用..."
adb -s $DEVICE_SERIAL shell am start -n $PACKAGE_NAME/.MainActivity > /dev/null 2>&1
echo "✓ 应用已启动"
echo ""

echo "=========================================="
echo "  📱 操作步骤"
echo "=========================================="
echo ""
echo "1. 在设备上点击 [开始录制] 按钮"
echo "2. (屏幕录制权限会失败，这是正常的)"
echo "3. 看到相机预览后，按 Home 键返回主屏幕"
echo "4. 等待 30 秒"
echo "5. 按 Ctrl+C 停止日志监控"
echo "6. 检查日志中是否有相机关闭的信息"
echo ""
echo "=========================================="
echo "  📊 关键指标"
echo "=========================================="
echo ""
echo "✅ 修复成功的标志："
echo "   - 看到: ServiceLifecycleOwner started, state: RESUMED"
echo "   - 进入后台后 NO 'Closing camera' 日志"
echo "   - 进入后台后 NO 'Use cases DETACHED' 日志"
echo ""
echo "❌ 修复失败的标志："
echo "   - 进入后台后看到: Closing camera"
echo "   - 进入后台后看到: Use cases DETACHED"
echo ""
echo "=========================================="
echo "  🔍 监控日志（Ctrl+C 停止）"
echo "=========================================="
echo ""

# 监控关键日志
adb -s $DEVICE_SERIAL logcat | grep --line-buffered -E "(RecordingService|ServiceLifecycleOwner|CameraRecorder|Camera2CameraImpl.*Closing|Camera2CameraImpl.*DETACHED|Camera2CameraImpl.*state|RecorderViewModelSimple.*cleared)"

