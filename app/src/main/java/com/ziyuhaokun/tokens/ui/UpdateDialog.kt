package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ziyuhaokun.tokens.data.ReleaseInfo
import com.ziyuhaokun.tokens.data.UpdateLogic
import com.ziyuhaokun.tokens.data.UpdateState

/** 设置页「检查更新」那一行显示的文字。 */
fun updateStatusLabel(state: UpdateState): String = when (state) {
    UpdateState.Idle -> "检查更新"
    UpdateState.Checking -> "正在检查更新…"
    UpdateState.UpToDate -> "已是最新版本（点此再查一次）"
    is UpdateState.Available -> "发现新版本 ${state.info.tag}，点此更新"
    is UpdateState.Downloading -> "正在下载新版本 ${percent(state.downloaded, state.total)}"
    is UpdateState.Ready -> "新版本已下载，点此安装"
    is UpdateState.Failed -> "${state.message}（点此重试）"
}

/** 有新版本要处理时，设置页那一行用醒目一点的颜色。 */
fun updateNeedsAttention(state: UpdateState): Boolean =
    state is UpdateState.Available || state is UpdateState.Ready

private fun percent(done: Long, total: Long): String =
    if (total > 0) "${(done * 100 / total).coerceIn(0, 100)}%" else "…"

/**
 * 更新弹窗：版本、更新说明、下载进度和按钮。
 * 点外面 / 返回键 = 「稍后」：下载不会被打断，设置页里还能再打开。
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    notice: String?,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val info: ReleaseInfo = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Failed -> state.info
        else -> null
    } ?: return

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SoftCard(modifier = Modifier.padding(horizontal = 24.dp)) {
            CardLabel("发现新版本")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "v$currentVersion → ${info.tag}",
                color = Palette.Dim,
                fontSize = TokensType.Caption,
                fontFamily = NumericFont,
            )

            val notes = UpdateLogic.plainNotes(info.notes)
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = notes,
                    color = Palette.TextSoft,
                    fontSize = TokensType.Caption,
                    lineHeight = TokensType.Caption * 1.5f,
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }

            when (state) {
                is UpdateState.Downloading -> {
                    Spacer(Modifier.height(14.dp))
                    if (state.total > 0) {
                        LinearProgressIndicator(
                            progress = { (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = Palette.Accent,
                            trackColor = Palette.Hairline,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Palette.Accent,
                            trackColor = Palette.Hairline,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    SmallLabel("正在下载 ${percent(state.downloaded, state.total)}")
                }

                is UpdateState.Ready -> {
                    Spacer(Modifier.height(12.dp))
                    SmallLabel("已下载并通过校验，点「安装」后在系统界面确认。", color = Palette.Ok)
                }

                is UpdateState.Failed -> {
                    Spacer(Modifier.height(12.dp))
                    SmallLabel(state.message, color = Palette.Warn)
                }

                else -> Unit
            }

            if (notice != null) {
                Spacer(Modifier.height(10.dp))
                SmallLabel(notice, color = Palette.Muted)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (state) {
                    is UpdateState.Available -> {
                        TextAction("跳过此版本", onClick = onSkip, color = Palette.Dim)
                        TextAction("稍后", onClick = onLater)
                        TextAction("更新", onClick = onUpdate, color = Palette.Text)
                    }

                    is UpdateState.Downloading -> {
                        TextAction("取消下载", onClick = onCancelDownload, color = Palette.Dim)
                        TextAction("后台下载", onClick = onLater)
                    }

                    is UpdateState.Ready -> {
                        TextAction("稍后", onClick = onLater)
                        TextAction("安装", onClick = onInstall, color = Palette.Text)
                    }

                    is UpdateState.Failed -> {
                        TextAction("稍后", onClick = onLater)
                        TextAction("重试", onClick = onUpdate, color = Palette.Text)
                    }

                    else -> Unit
                }
            }
        }
    }
}
