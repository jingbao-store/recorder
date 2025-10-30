#!/bin/bash

# 后台录制测试脚本
# 用于验证后台录制修复是否生效

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_NAME="com.jingbao.recorder"
DEVICE_SERIAL="1901092534000358"
TEST_DURATION=30

echo "=========================================="
echo "  后台录制功能测试"
echo "=========================================="
echo ""

# 设置 JAVA_HOME
if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    echo "✓ 使用 Android Studio 自带的 JBR: $JAVA_HOME"
else
    echo "⚠️  未找到 Android Studio JBR，使用系统默认 Java"
fi
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# 检查设备连接
echo "1. 检查设备连接..."
if ! adb -s $DEVICE_SERIAL get-state > /dev/null 2>&1; then
    print_error "设备 $DEVICE_SERIAL 未连接"
    echo ""
    echo "可用设备："
    adb devices
    exit 1
fi
print_success "设备已连接"
echo ""

# 编译应用
echo "2. 编译应用..."
cd "$SCRIPT_DIR"
if ./gradlew assembleDebug > /dev/null 2>&1; then
    print_success "编译成功"
else
    print_error "编译失败"
    exit 1
fi
echo ""

# 安装应用
echo "3. 安装应用..."
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if adb -s $DEVICE_SERIAL install -r "$APK_PATH" > /dev/null 2>&1; then
    print_success "安装成功"
else
    print_error "安装失败"
    exit 1
fi
echo ""

# 清理旧日志
adb -s $DEVICE_SERIAL logcat -c

# 启动应用
echo "4. 启动应用..."
adb -s $DEVICE_SERIAL shell am start -n $PACKAGE_NAME/.MainActivity > /dev/null 2>&1
sleep 2
print_success "应用已启动"
echo ""

# 监控日志
echo "5. 监控录制日志..."
print_info "请在设备上执行以下操作："
echo "   1. 点击开始录制按钮"
echo "   2. 授予屏幕录制权限"
echo "   3. 等待录制开始（看到相机预览）"
echo ""

# 等待录制开始
print_info "等待录制开始..."
TIMEOUT=30
ELAPSED=0
RECORDING_STARTED=false

while [ $ELAPSED -lt $TIMEOUT ]; do
    if adb -s $DEVICE_SERIAL logcat -d | grep -q "Recording started successfully in service"; then
        RECORDING_STARTED=true
        break
    fi
    sleep 1
    ELAPSED=$((ELAPSED + 1))
    echo -ne "\r   等待中... ${ELAPSED}s / ${TIMEOUT}s"
done
echo ""

if [ "$RECORDING_STARTED" = false ]; then
    print_error "录制未能在 ${TIMEOUT} 秒内启动"
    print_info "请检查是否授予了所有必要权限"
    exit 1
fi

print_success "录制已开始"
echo ""

# 检查 ServiceLifecycleOwner
if adb -s $DEVICE_SERIAL logcat -d | grep -q "ServiceLifecycleOwner started"; then
    print_success "ServiceLifecycleOwner 已启动"
    
    # 获取生命周期状态
    LIFECYCLE_STATE=$(adb -s $DEVICE_SERIAL logcat -d | grep "ServiceLifecycleOwner started, state:" | tail -1 | sed -n 's/.*state: \([A-Z]*\).*/\1/p')
    if [ -n "$LIFECYCLE_STATE" ]; then
        print_info "生命周期状态: $LIFECYCLE_STATE"
    fi
else
    print_error "未找到 ServiceLifecycleOwner 启动日志"
fi
echo ""

# 将应用切换到后台
echo "6. 将应用切换到后台..."
adb -s $DEVICE_SERIAL shell input keyevent KEYCODE_HOME
sleep 2
print_success "应用已切换到后台"
echo ""

# 检查相机状态
echo "7. 检查后台录制状态..."
sleep 3

# 检查是否有相机关闭的日志
if adb -s $DEVICE_SERIAL logcat -d -t 100 | grep -q "Closing camera"; then
    print_error "相机已关闭（修复失败）"
    echo ""
    echo "相机关闭日志："
    adb -s $DEVICE_SERIAL logcat -d | grep "Camera.*Closing" | tail -5
    exit 1
else
    print_success "相机保持运行（修复成功）"
fi

# 检查是否有 Use cases DETACHED 的日志
if adb -s $DEVICE_SERIAL logcat -d -t 100 | grep -q "Use cases.*DETACHED"; then
    print_error "相机用例已分离（修复失败）"
    exit 1
else
    print_success "相机用例保持连接"
fi
echo ""

# 在后台运行一段时间
echo "8. 后台录制测试（${TEST_DURATION}秒）..."
for i in $(seq 1 $TEST_DURATION); do
    echo -ne "\r   录制中... ${i}s / ${TEST_DURATION}s"
    sleep 1
    
    # 每 10 秒检查一次相机状态
    if [ $((i % 10)) -eq 0 ]; then
        if adb -s $DEVICE_SERIAL logcat -d -t 50 | grep -q "Closing camera"; then
            echo ""
            print_error "相机在后台被关闭"
            exit 1
        fi
    fi
done
echo ""
print_success "后台录制测试完成"
echo ""

# 停止录制
echo "9. 停止录制..."
# 重新打开应用
adb -s $DEVICE_SERIAL shell am start -n $PACKAGE_NAME/.MainActivity > /dev/null 2>&1
sleep 2

print_info "请在设备上点击停止录制按钮"
echo ""

# 等待录制停止
print_info "等待录制停止..."
TIMEOUT=20
ELAPSED=0
RECORDING_STOPPED=false

while [ $ELAPSED -lt $TIMEOUT ]; do
    if adb -s $DEVICE_SERIAL logcat -d | grep -q "Recording stopped successfully"; then
        RECORDING_STOPPED=true
        break
    fi
    sleep 1
    ELAPSED=$((ELAPSED + 1))
    echo -ne "\r   等待中... ${ELAPSED}s / ${TIMEOUT}s"
done
echo ""

if [ "$RECORDING_STOPPED" = true ]; then
    print_success "录制已停止"
else
    print_info "请手动停止录制"
fi
echo ""

# 检查视频文件
echo "10. 检查视频文件..."
VIDEO_FILES=$(adb -s $DEVICE_SERIAL shell "ls -t /sdcard/Movies/Camera/AR_Recording_*.mp4 2>/dev/null" | head -1)

if [ -n "$VIDEO_FILES" ]; then
    VIDEO_FILE=$(echo "$VIDEO_FILES" | tr -d '\r')
    FILE_SIZE=$(adb -s $DEVICE_SERIAL shell "du -h '$VIDEO_FILE'" | awk '{print $1}')
    print_success "视频文件已生成: $VIDEO_FILE"
    print_info "文件大小: $FILE_SIZE"
    
    # 询问是否拉取视频
    echo ""
    read -p "是否拉取视频到本地？(y/n) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        OUTPUT_FILE="./test_recording_$(date +%Y%m%d_%H%M%S).mp4"
        adb -s $DEVICE_SERIAL pull "$VIDEO_FILE" "$OUTPUT_FILE"
        print_success "视频已保存到: $OUTPUT_FILE"
    fi
else
    print_error "未找到视频文件"
fi
echo ""

# 显示关键日志
echo "=========================================="
echo "  关键日志摘要"
echo "=========================================="
echo ""

echo "ServiceLifecycleOwner 日志:"
adb -s $DEVICE_SERIAL logcat -d | grep "ServiceLifecycleOwner" | tail -5
echo ""

echo "相机状态日志:"
adb -s $DEVICE_SERIAL logcat -d | grep -E "(Camera.*opened|Camera.*closed|Camera.*state)" | tail -10
echo ""

echo "录制状态日志:"
adb -s $DEVICE_SERIAL logcat -d | grep "RecordingService" | grep -E "(started|stopped|error)" | tail -5
echo ""

echo "=========================================="
print_success "测试完成！"
echo "=========================================="

