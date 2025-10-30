package com.jingbao.recorder.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.jingbao.recorder.model.RecordingState
import com.jingbao.recorder.viewmodel.RecorderViewModelSimple

/**
 * Rokid 眼镜专用录制界面
 * - 适配 480x640 竖屏
 * - 支持方向键导航
 * - 遵循 Rokid 设计规范
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RokidRecorderScreen(
    viewModel: RecorderViewModelSimple = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    val recordingState by viewModel.recordingState.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val shouldMinimizeApp by viewModel.shouldMinimizeApp.collectAsState()
    
    // Rokid 设计规范颜色
    val RokidGreen = Color(0xFF40FF5E)
    
    // 权限请求
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    )
    
    // 屏幕录制权限
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onScreenCaptureResult(context, result.resultCode, result.data)
    }
    
    // 注册广播接收器
    DisposableEffect(Unit) {
        viewModel.registerReceivers(context)
        onDispose {
            viewModel.unregisterReceivers(context)
        }
    }
    
    // 更新权限状态
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        viewModel.updatePermissions(permissionsState.allPermissionsGranted)
    }
    
    // 手动后台运行（移除自动最小化）
    // LaunchedEffect(shouldMinimizeApp) {
    //     if (shouldMinimizeApp) {
    //         activity?.moveTaskToBack(true)
    //         viewModel.resetMinimizeFlag()
    //     }
    // }
    
    // 主界面 - 遵循 Rokid 480*640px 设计规范
    // 内容安全区域：480*400px（顶部留白 160px，底部留白 80px）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 160.dp,    // 顶部安全区域（不可用）
                    bottom = 80.dp,  // 底部安全区域（预留系统按钮）
                    start = 24.dp,
                    end = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：标题
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题 - 一级字号 32px/40px
                Text(
                    text = "小镜录像",
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Medium,
                    color = RokidGreen
                )
                
                // 副标题删除
            }
            
            // 中间：录制状态和时长
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // 录制状态指示器（红点）
                if (recordingState == RecordingState.RECORDING) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFFF4444), CircleShape)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // 状态文本 - 二级字号 24px/32px
                Text(
                    text = when (recordingState) {
                        RecordingState.IDLE -> "待机"
                        RecordingState.RECORDING -> "录制中"
                        RecordingState.STOPPED -> "已停止"
                        RecordingState.PAUSED -> "已暂停"
                    },
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (recordingState == RecordingState.RECORDING) {
                        Color(0xFFFF4444)
                    } else {
                        Color.White
                    }
                )
                
                // 录制时长 - 一级字号 32px/40px（重点显示）
                if (recordingState == RecordingState.RECORDING) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = formatDuration(recordingDuration),
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = RokidGreen,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                
                // 提示文字 - 五级字号 16px/22px
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = when (recordingState) {
                        RecordingState.IDLE -> "按确认键开始录制"
                        RecordingState.RECORDING -> "↑↓ 切换按钮\n确认键选择操作"
                        RecordingState.STOPPED -> "录制完成"
                        RecordingState.PAUSED -> "录制暂停"
                    },
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
            
            // 底部：控制按钮
            if (!permissionsState.allPermissionsGranted) {
                RokidPermissionButton(
                    onClick = {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                )
            } else {
                when (recordingState) {
                    RecordingState.IDLE, RecordingState.STOPPED -> {
                        // 待机状态：只显示"开始录制"按钮
                        RokidRecordButton(
                            text = "开始录制",
                            isRecording = false,
                            onClick = {
                                viewModel.requestScreenCapture(context, screenCaptureLauncher)
                            }
                        )
                    }
                    RecordingState.RECORDING -> {
                        // 录制中：显示"后台运行"和"停止录制"两个按钮
                        RokidDualButtons(
                            onMinimize = {
                                activity?.moveTaskToBack(true)
                            },
                            onStop = {
                                viewModel.stopRecording(context)
                            }
                        )
                    }
                    else -> {}
                }
            }
        }
        
        // 错误提示（如果有）
        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                Text(
                    text = error,
                    fontSize = 14.sp,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                )
            }
            
            LaunchedEffect(error) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearError()
            }
        }
    }
}

/**
 * Rokid 风格的录制按钮
 * 三种状态：常态（40%）、选中（80%）、按下（100%）
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RokidRecordButton(
    text: String,
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val RokidGreen = Color(0xFF40FF5E)
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    
    // 根据状态计算描边透明度
    val borderAlpha = when {
        isPressed -> 1.0f        // 按下：100%
        isFocused -> 0.8f        // 选中：80%
        else -> 0.4f             // 常态：40%
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.Transparent)  // 背景透明
            .border(
                width = 2.dp,
                color = RokidGreen.copy(alpha = borderAlpha),  // 描边透明度变化
                shape = RoundedCornerShape(12.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                // 参考 Rokid 游戏：KeyEvent.KEYCODE_ENTER, KEYCODE_DPAD_CENTER, KEYCODE_SPACE
                when (keyEvent.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_SPACE -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            isPressed = true
                            true
                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                            isPressed = false
                            if (isFocused) {
                                onClick()
                            }
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        // 按钮文字 - 二级字号 24px/32px，白色
        Text(
            text = text,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White  // 白色文字
        )
    }
}

/**
 * Rokid 风格的权限按钮
 * 三种状态：常态（40%）、选中（80%）、按下（100%）
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RokidPermissionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val RokidGreen = Color(0xFF40FF5E)
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    
    // 根据状态计算描边透明度
    val borderAlpha = when {
        isPressed -> 1.0f        // 按下：100%
        isFocused -> 0.8f        // 选中：80%
        else -> 0.4f             // 常态：40%
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // 提示文字 - 三级字号 20px/26px
        Text(
            text = "需要授予权限",
            fontSize = 20.sp,
            lineHeight = 26.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.Transparent)  // 背景透明
                .border(
                    width = 2.dp,
                    color = RokidGreen.copy(alpha = borderAlpha),  // 描边透明度变化
                    shape = RoundedCornerShape(12.dp)
                )
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .onKeyEvent { keyEvent ->
                    // 参考 Rokid 游戏：KeyEvent.KEYCODE_ENTER, KEYCODE_DPAD_CENTER, KEYCODE_SPACE
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_SPACE -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                isPressed = true
                                true
                            } else if (keyEvent.type == KeyEventType.KeyUp) {
                                isPressed = false
                                if (isFocused) {
                                    onClick()
                                }
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            // 按钮文字 - 二级字号 24px/32px，白色
            Text(
                text = "授予权限",
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White  // 白色文字
            )
        }
    }
}

/**
 * Rokid 双按钮（录制中显示）
 * 支持方向键导航
 * 描边样式，背景透明
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RokidDualButtons(
    onMinimize: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester1 = remember { FocusRequester() }
    val focusRequester2 = remember { FocusRequester() }
    var focusedButton by remember { mutableStateOf(1) } // 1=后台运行, 2=停止录制
    
    // 默认焦点在第一个按钮
    LaunchedEffect(Unit) {
        focusRequester1.requestFocus()
        focusedButton = 1
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 按钮 1：后台运行
        RokidNavigableButton(
            text = "后台运行",
            focusRequester = focusRequester1,
            isFocused = focusedButton == 1,
            onFocusChanged = { focused ->
                if (focused) focusedButton = 1
            },
            onClick = onMinimize,
            onNavigateDown = {
                focusRequester2.requestFocus()
            }
        )
        
        // 按钮 2：停止录制
        RokidNavigableButton(
            text = "停止录制",
            focusRequester = focusRequester2,
            isFocused = focusedButton == 2,
            onFocusChanged = { focused ->
                if (focused) focusedButton = 2
            },
            onClick = onStop,
            onNavigateUp = {
                focusRequester1.requestFocus()
            }
        )
    }
}

/**
 * Rokid 可导航按钮
 * 支持上下方向键导航
 * 三种状态：常态（40%）、选中（80%）、按下（100%）
 * 描边样式，背景透明，白色文字
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RokidNavigableButton(
    text: String,
    focusRequester: FocusRequester,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val RokidGreen = Color(0xFF40FF5E)
    var isPressed by remember { mutableStateOf(false) }
    
    // 根据状态计算描边透明度
    val borderAlpha = when {
        isPressed -> 1.0f        // 按下：100%
        isFocused -> 0.8f        // 选中：80%
        else -> 0.4f             // 常态：40%
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.Transparent)  // 背景透明
            .border(
                width = 2.dp,
                color = RokidGreen.copy(alpha = borderAlpha),  // 描边透明度变化
                shape = RoundedCornerShape(12.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { 
                onFocusChanged(it.isFocused)
            }
            .onKeyEvent { keyEvent ->
                // 参考 Rokid 游戏：使用 nativeKeyEvent.keyCode
                when (keyEvent.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_SPACE -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            isPressed = true
                            true
                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                            isPressed = false
                            if (isFocused) {
                                onClick()
                            }
                            true
                        } else {
                            false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            onNavigateUp?.invoke()
                            true
                        } else false
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            onNavigateDown?.invoke()
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        // 按钮文字 - 二级字号 24px/32px，白色
        Text(
            text = text,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White  // 白色文字
        )
    }
}

/**
 * 格式化时长显示
 */
private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = millis / (1000 * 60 * 60)
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

