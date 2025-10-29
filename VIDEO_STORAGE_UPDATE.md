# 视频存储位置更新说明

## 📋 问题分析

### photoView4rokidglasses 项目查询路径
在 `PhotoListActivity.kt` 的 `loadAllMediaUris()` 方法中，该项目查询以下目录的媒体文件：

1. **图片**：
   - `DCIM/Camera`
   - `Pictures`

2. **视频**：
   - `DCIM/Camera`
   - `Pictures`
   - `Movies`
   - `Movies/Camera`

### recorder 项目原存储路径
之前 recorder 项目将视频保存在：
- ❌ `DIRECTORY_MOVIES/ARRecorder` → `/sdcard/Movies/ARRecorder/`

**问题**：photoView4rokidglasses 只查询 `/Movies/` 和 `/Movies/Camera/`，**不会**查询 `/Movies/ARRecorder/` 子目录，导致无法找到 recorder 录制的视频。

## ✅ 解决方案

### 修改存储路径
将 recorder 的视频存储路径改为：
- ✅ `DIRECTORY_MOVIES/Camera` → `/sdcard/Movies/Camera/`

**优势**：
1. photoView4rokidglasses 会查询这个目录
2. **与系统相机录制视频存储在同一位置**，符合用户习惯
3. 组织有序，相机相关内容集中管理
4. 两个应用完全兼容

## 🔧 代码修改

### RecordingService.kt
**文件位置**：`app/src/main/java/com/jingbao/recorder/service/RecordingService.kt`

**修改内容**：
```kotlin
// 修改前
private fun createOutputFile(): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "AR_Recording_$timestamp.mp4"
    
    val moviesDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        "ARRecorder"
    )
    
    if (!moviesDir.exists()) {
        moviesDir.mkdirs()
    }
    
    val file = File(moviesDir, fileName)
    Log.d(TAG, "Output file: ${file.absolutePath}")
    return file
}

// 修改后
private fun createOutputFile(): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "AR_Recording_$timestamp.mp4"
    
    // 使用 DIRECTORY_MOVIES/Camera，与相机录制内容存储在同一位置
    val cameraDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        "Camera"
    )
    
    if (!cameraDir.exists()) {
        cameraDir.mkdirs()
    }
    
    val file = File(cameraDir, fileName)
    Log.d(TAG, "Output file: ${file.absolutePath}")
    return file
}
```

## 📝 文档更新

以下文档已同步更新路径信息：

1. ✅ `README.md` - 使用说明
2. ✅ `TESTING_GUIDE.md` - 测试指南
3. ✅ `PROJECT_SUMMARY.md` - 项目总结
4. ✅ `QUICK_TEST.md` - 快速测试
5. ✅ `QUICK_NAV_TEST.md` - 导航测试
6. ✅ `QUICK_FIX_TEST.md` - 修复测试
7. ✅ `NAVIGATION_GUIDE.md` - 导航指南
8. ✅ `EOS_ERROR_FIX.md` - EOS错误修复
9. ✅ `CRITICAL_FIXES.md` - 关键修复
10. ✅ `BACKGROUND_RECORDING.md` - 后台录制

## 🧪 测试验证

### 测试步骤

1. **录制视频**
   ```bash
   # 重新安装应用
   ./gradlew installDebug
   
   # 或使用安装脚本
   ./install.sh
   ```

2. **启动录制**
   - 打开 AR Recorder 应用
   - 点击红色按钮开始录制
   - 录制一段视频后停止

3. **检查视频位置**
   ```bash
   # 查看新的存储位置
   adb -s 1901092534000358 shell ls -lh /sdcard/Movies/Camera/
   
   # 应该能看到 AR_Recording_*.mp4 文件
   ```

4. **使用 photoView4rokidglasses 查看**
   - 打开 photoView4rokidglasses 应用
   - 应该能看到刚录制的视频
   - 可以浏览、播放和删除

5. **导出视频到电脑**
   ```bash
   # 拉取视频文件
   adb -s 1901092534000358 pull /sdcard/Movies/Camera/AR_Recording_*.mp4 ./
   
   # 播放验证（macOS）
   open AR_Recording_*.mp4
   ```

### 预期结果

✅ 视频保存在 `/sdcard/Movies/Camera/` 目录
✅ photoView4rokidglasses 可以查询到视频
✅ 可以在 photoView4rokidglasses 中查看、播放视频
✅ 可以在 photoView4rokidglasses 中删除视频
✅ 两个应用完全兼容

## 📊 路径对比

| 项目 | 原路径 | 新路径 | photoView 兼容 |
|------|--------|--------|----------------|
| recorder | `/sdcard/Movies/ARRecorder/` | `/sdcard/Movies/Camera/` | ✅ |

## 🎯 总结

通过将 recorder 的视频存储路径从 `DIRECTORY_MOVIES/ARRecorder` 改为 `DIRECTORY_MOVIES/Camera`，实现了：

1. **完全兼容**：photoView4rokidglasses 可以查询到 recorder 录制的视频
2. **标准化**：与系统相机录制视频存储在同一位置
3. **组织有序**：相机相关的图片和视频集中在 Camera 目录下
4. **用户友好**：符合用户在 Camera 目录查找相机内容的习惯
5. **语义清晰**：AR 录屏作为相机类应用，内容存储在 Camera 目录很合理

---

**修改日期**：2025-10-29
**修改人**：AI Assistant
**相关项目**：
- recorder (AR 录屏应用)
- photoView4rokidglasses (图片/视频查看器)

