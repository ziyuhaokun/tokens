package com.ziyuhaokun.tokens.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ziyuhaokun.tokens.data.NumberFormats
import com.ziyuhaokun.tokens.data.ReceiptData
import com.ziyuhaokun.tokens.export.ReceiptExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 打印一整个小票的时长；几段「顿一下」的节奏都排在这条时间轴上。 */
private const val PrintDurationMillis = 1100

/** 打印时轻震的时刻（毫秒），跟 [PrintKeyframes] 里每次跳档对齐。 */
private val PrintTicks = listOf(130L, 290L, 460L, 640L, 810L)

private val Scrim = Color.Black.copy(alpha = 0.55f)
private val ActionPill = Color(0xFF1A1A1A).copy(alpha = 0.82f)
private val ActionPillDisabled = Color(0xFF1A1A1A).copy(alpha = 0.38f)

/**
 * 小票浮层：从屏幕顶端「打印」出来的一张纸，底部三个操作（保存 / 分享 / 关闭）。
 *
 * 三层结构，各自的职责不能混：
 * 1. 最外层是遮罩，点一下关掉；返回键同样关掉。
 * 2. 中间一层只做揭示裁剪（[printReveal]），它必须在录制之外，否则存下来的图只有露出的那截。
 * 3. 卡片自己带 [captureInto]，把完整内容录进 graphicsLayer，保存 / 分享都取这一层的位图。
 */
@Composable
fun ReceiptSheet(data: ReceiptData, formats: NumberFormats, onDismiss: () -> Unit) {
    val motion = LocalMotionEnabled.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    val progress = remember { Animatable(if (motion) 0f else 1f) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) { onDismiss() }

    LaunchedEffect(Unit) {
        if (!motion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.animateTo(1f, animationSpec = PrintKeyframes)
    }

    // 打印时的走纸震动：只在有动态效果时给，精简模式完全不震。
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        var elapsed = 0L
        PrintTicks.forEach { at ->
            delay(at - elapsed)
            elapsed = at
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // 结果提示 2 秒后自己消失。
    LaunchedEffect(message) {
        if (message != null) {
            delay(2000)
            message = null
        }
    }

    val fileName = "tokens-receipt-${data.number}.png"

    val saveImage: suspend () -> Unit = {
        busy = true
        try {
            val bitmap = graphicsLayer.toImageBitmap()
            message = ReceiptExporter.saveToGallery(context, bitmap, fileName).fold(
                onSuccess = { "已保存到相册" },
                onFailure = { "保存失败：${it.message ?: "未知错误"}" },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "保存失败：${e.message ?: "未知错误"}"
        } finally {
            busy = false
        }
    }

    val shareImage: suspend () -> Unit = {
        busy = true
        try {
            val bitmap = graphicsLayer.toImageBitmap()
            ReceiptExporter.share(context, bitmap, fileName).onFailure {
                message = "分享失败：${it.message ?: "未知错误"}"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "分享失败：${e.message ?: "未知错误"}"
        } finally {
            busy = false
        }
    }

    // Android 9 及以下先要写相册权限，拿到再存；10+ 走 MediaStore，不需要。
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) scope.launch { saveImage() } else message = "保存失败：没有相册写入权限"
    }

    val onSave: () -> Unit = {
        val needsPermission = ReceiptExporter.needsLegacyWritePermission &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            scope.launch { saveImage() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩：点空白处关闭。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                // 吞掉纸面上的点击，别让它们穿到遮罩上把小票关掉。
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.printReveal { progress.value }) {
                    ReceiptCard(
                        data = data,
                        formats = formats,
                        modifier = Modifier.captureInto(graphicsLayer),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // 固定高度的提示行：有没有提示按钮都不跳。
            Box(
                modifier = Modifier.fillMaxWidth().height(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                message?.let { text ->
                    Text(text = text, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton("保存", enabled = !busy, modifier = Modifier.weight(1f), onClick = onSave)
                PillButton(
                    text = "分享",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { scope.launch { shareImage() } },
                )
                PillButton("关闭", enabled = true, modifier = Modifier.weight(1f), onClick = onDismiss)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 打印节奏：纸一格一格往下走，中间偶尔顿一下，最后慢慢贴平。 */
private val PrintKeyframes = keyframes<Float> {
    durationMillis = PrintDurationMillis
    0f at 0 using LinearEasing
    0.16f at 80 using LinearEasing
    0.19f at 140 using LinearEasing
    0.42f at 230 using LinearEasing
    0.45f at 300 using LinearEasing
    0.68f at 400 using LinearEasing
    0.71f at 470 using LinearEasing
    0.88f at 570 using LinearEasing
    0.90f at 650 using LinearEasing
    0.97f at 800 using LinearEasing
    1f at 1100 using LinearEasing
}

/**
 * 从上往下的揭示裁剪。画在录制层外面，所以被裁掉的只是屏幕上的画面，
 * 存下来的小票图始终是完整的。
 */
private fun Modifier.printReveal(progress: () -> Float): Modifier = drawWithContent {
    val fraction = progress().coerceIn(0f, 1f)
    if (fraction <= 0f) return@drawWithContent
    clipRect(right = size.width, bottom = size.height * fraction) {
        this@drawWithContent.drawContent()
    }
}

/**
 * 把这张卡片画进 graphicsLayer 再画出来：保存 / 分享取的就是这一层，
 * 与它在屏幕上被裁掉多少无关。
 */
private fun Modifier.captureInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

@Composable
private fun PillButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (enabled) ActionPill else ActionPillDisabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
