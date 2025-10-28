#!/bin/bash

# AR 录制器 - 快速安装脚本

echo "================================================"
echo "  AR 画中画录制器 - 安装脚本"
echo "================================================"
echo ""

# 检查 ADB
if ! command -v adb &> /dev/null; then
    echo "❌ 错误：ADB 未找到，请先安装 Android SDK Platform Tools"
    exit 1
fi

echo "✓ ADB 已就绪"

# 检查设备连接
echo ""
echo "正在检查设备连接..."
DEVICE=$(adb devices | grep "1901092534000358" | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo ""
    echo "❌ Rokid 设备未连接（设备 ID: 1901092534000358）"
    echo ""
    echo "请执行以下步骤："
    echo "1. 确保 Rokid 设备已通过 USB 连接到电脑"
    echo "2. 在 Rokid 设备上启用 USB 调试"
    echo "3. 运行 'adb devices' 确认设备已连接"
    echo ""
    exit 1
fi

echo "✓ Rokid 设备已连接: $DEVICE"

# 检查 APK 文件
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo ""
    echo "⚠️  APK 文件不存在，正在构建..."
    echo ""
    
    if [ ! -f "./gradlew" ]; then
        echo "❌ 错误：gradlew 文件未找到，请在项目根目录运行此脚本"
        exit 1
    fi
    
    ./gradlew assembleDebug
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "❌ 构建失败，请检查错误信息"
        exit 1
    fi
fi

echo "✓ APK 文件已就绪: $APK_PATH"

# 安装 APK
echo ""
echo "正在安装应用到 Rokid 设备..."
adb -s $DEVICE install -r $APK_PATH

if [ $? -eq 0 ]; then
    echo ""
    echo "================================================"
    echo "  ✅ 安装成功！"
    echo "================================================"
    echo ""
    echo "应用已安装到 Rokid 设备"
    echo "应用名称：AR Recorder"
    echo "包名：com.jingbao.recorder"
    echo ""
    echo "启动应用："
    echo "  方法 1：在 Rokid 设备上找到 'AR Recorder' 图标并点击"
    echo "  方法 2：运行命令："
    echo "          adb -s $DEVICE shell am start -n com.jingbao.recorder/.MainActivity"
    echo ""
    echo "查看日志："
    echo "  adb -s $DEVICE logcat -s RecorderViewModel:D"
    echo ""
    echo "使用说明请参考 TESTING_GUIDE.md"
    echo ""
else
    echo ""
    echo "❌ 安装失败"
    echo ""
    echo "可能的原因："
    echo "1. 设备空间不足"
    echo "2. 权限问题"
    echo "3. 已安装的版本签名不匹配（需要先卸载旧版本）"
    echo ""
    echo "尝试卸载旧版本后重新安装："
    echo "  adb -s $DEVICE uninstall com.jingbao.recorder"
    echo "  $0"
    echo ""
    exit 1
fi

