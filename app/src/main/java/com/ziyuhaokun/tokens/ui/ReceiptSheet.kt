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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ziyuhaokun.tokens.data.ReceiptLanguage
import com.ziyuhaokun.tokens.data.ReceiptStyle
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

/** 出纸口那条黑条的半透明度。 */
private const val SlotAlpha = 0.6f

/** 纸的投影需要一点余量，裁剪框的左右 / 下方各留这么多。 */
private val SlotShadowRoom = 12.dp

/**
 * 小票浮层：从屏幕顶端的出纸口「打印」出来的一张纸，底部三个操作（保存 / 分享 / 关闭）。
 *
 * 三层结构，各自的职责不能混：
 * 1. 最外层是遮罩；空白处（含纸旁边、纸下方的空档）点一下关掉，返回键同样关掉。
 * 2. 中间一层只做「出纸」：裁剪线固定在出纸口，纸从上方平移下来。它必须在录制之外，
 *    否则存下来的图只有露出的那截。
 * 3. 卡片自己带 [captureInto]，把完整内容录进 graphicsLayer，保存 / 分享都取这一层的位图。
 */
@Composable
fun ReceiptSheet(
    data: ReceiptData,
    formats: NumberFormats,
    style: ReceiptStyle,
    language: ReceiptLanguage,
    onDismiss: () -> Unit,
) {
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
        // 遮罩：最底下一层，点空白处关闭。
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
                // 遮罩上面这层盖住了整屏，空白处的点击得由它自己接住再关掉；
                // 纸和按钮各自吞掉属于自己的点击，不往下传。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                PaperSlot(progress = { progress.value }) {
                    ReceiptCard(
                        data = data,
                        formats = formats,
                        style = style,
                        language = language,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    // 按钮行内部的空白也不该关掉浮层。
                    .pointerInput(Unit) { detectTapGestures { } },
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
 * 出纸：裁剪线固定在顶部出纸口，纸本身从上方平移下来（-整张高度 → 0）。
 *
 * 平移用的是 layout 层（graphicsLayer），裁剪用的是外层的 clipToBounds，两者都在
 * [captureInto] 的录制层外面 —— 所以屏幕上只露出一截，存下来的小票始终是完整的一张。
 */
@Composable
private fun PaperSlot(progress: () -> Float, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 左右 / 下方留出投影的余量，顶部一点不留：那边就是裁剪线。
            .padding(start = SlotShadowRoom, end = SlotShadowRoom, bottom = SlotShadowRoom)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    // 0 → 纸在裁剪线上面一整张（看不见）；1 → 正好落位。
                    translationY = -(1f - progress().coerceIn(0f, 1f)) * size.height
                }
                // 纸面上的点击不往下传，要不点小票本身就被当成「点空白关掉」。
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            content()
        }
        // 出纸口：纸已经在外面了（progress = 1）就淡到看不见。
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(ReceiptWidth + 12.dp)
                .height(4.dp)
                .drawBehind {
                    val alpha = (1f - progress().coerceIn(0f, 1f)) * SlotAlpha
                    if (alpha <= 0.01f) return@drawBehind
                    drawRoundRect(
                        color = Color.Black.copy(alpha = alpha),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
        )
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
