package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.AccentChoice
import com.ziyuhaokun.tokens.data.FontScaleChoice
import com.ziyuhaokun.tokens.data.StoredSettings
import com.ziyuhaokun.tokens.data.ThemeMode

/**
 * 设置独立成页：左边标签、右边控件，一屏能看全。
 * 所有选项点一下就生效（写 DataStore），不需要「保存」。
 */
@Composable
fun SettingsScreen(
    settings: StoredSettings?,
    versionName: String,
    saving: Boolean,
    saveError: String?,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentChoice) -> Unit,
    onFontScale: (FontScaleChoice) -> Unit,
    onMotion: (Boolean) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onClearCache: () -> Unit,
    onRefreshAll: () -> Unit,
) {
    var showCredentials by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(14.dp))

        // 返回 + 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "← 返回",
                color = Palette.Muted,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("设置", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Light)
        Text(
            text = settings?.apiKey?.let { "API Key 已配置 · ${maskKey(it)}" } ?: "未配置 API Key",
            color = Palette.Dim,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(18.dp))

        // 外观
        SettingsGroup("外观") {
            SettingsRow("主题") {
                SegmentedRow(
                    options = ThemeMode.entries.map { it to it.label },
                    selected = settings?.themeMode ?: ThemeMode.SYSTEM,
                    onSelect = onThemeMode,
                )
            }
            HairlineDivider()
            SettingsRow("文字大小") {
                SegmentedRow(
                    options = FontScaleChoice.entries.map { it to it.label },
                    selected = settings?.fontScale ?: FontScaleChoice.STANDARD,
                    onSelect = onFontScale,
                )
            }
            HairlineDivider()
            SettingsRow("动态效果") {
                SegmentedRow(
                    options = listOf(true to "完整", false to "精简"),
                    selected = settings?.motion ?: true,
                    onSelect = onMotion,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 强调色
        SettingsGroup("配色") {
            SettingsRow("强调色") {
                AccentRow(
                    selected = settings?.accent ?: AccentChoice.MONO,
                    onSelect = onAccent,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "只作用于大金额、选中的标签、图表主线和热力图最亮档。选「单色」就是纯黑白。",
                color = Palette.Faint,
                fontSize = 9.sp,
                lineHeight = 13.sp,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 数据
        SettingsGroup("数据") {
            SettingsRow("服务器") {
                Text(
                    text = settings?.baseUrl ?: "—",
                    color = Palette.TextSoft,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
            HairlineDivider()
            SettingsRow("API Key") {
                Text(
                    text = settings?.apiKey?.let { maskKey(it) } ?: "未配置",
                    color = Palette.Dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            HairlineDivider()
            ActionRow("更新 Key 或服务器地址") { showCredentials = !showCredentials }
            if (showCredentials) {
                Spacer(Modifier.height(10.dp))
                CredentialsForm(
                    initialKey = "",
                    initialUrl = settings?.baseUrl ?: "https://vibecafe.ai",
                    saving = saving,
                    error = saveError,
                    saveLabel = "保存",
                    onSave = { key, url ->
                        showCredentials = false
                        onSaveCredentials(key, url)
                    },
                    onCancel = { showCredentials = false },
                )
            }
            HairlineDivider()
            ActionRow(
                label = if (cleared) "缓存已清除" else "清除本地缓存",
                valueColor = if (cleared) Palette.Ok else null,
            ) {
                onClearCache()
                cleared = true
                onRefreshAll()
            }
            HairlineDivider()
            ActionRow("立即刷新") { onRefreshAll() }
        }

        Spacer(Modifier.height(12.dp))

        // 关于
        SettingsGroup("关于") {
            SettingsRow("版本") {
                Text(
                    text = "v$versionName",
                    color = Palette.TextSoft,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            HairlineDivider()
            SettingsRow("用量口径") {
                Text(
                    text = "费用 = 服务端预估值之和；tokens 含缓存读与缓存写",
                    color = Palette.Dim,
                    fontSize = 10.sp,
                )
            }
            HairlineDivider()
            ActionRow("退出登录（清除 Key）", valueColor = Palette.Warn) { onSignOut() }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            color = Palette.Dim,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        SoftCard(contentPadding = 14) { content() }
    }
}

/** 左标签右控件。控件太宽时允许换行到下一行。 */
@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Palette.TextSoft,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        content()
    }
}

@Composable
private fun ActionRow(label: String, valueColor: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    Text(
        text = label,
        color = valueColor ?: Palette.TextSoft,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** 一行分段选择器，选中项用强调色描边。 */
@Composable
private fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val isOn = value == selected
            Text(
                text = label,
                color = if (isOn) Palette.Text else Palette.Dim,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isOn) Palette.Field else androidx.compose.ui.graphics.Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isOn) Palette.Accent.copy(alpha = 0.55f) else androidx.compose.ui.graphics.Color.Transparent,
                        shape = RoundedCornerShape(7.dp),
                    )
                    .clickable { if (!isOn) onSelect(value) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun AccentRow(selected: AccentChoice, onSelect: (AccentChoice) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentChoice.entries.forEach { choice ->
            val color = accentColor(choice, LocalTokensColors.current.isDark)
            val isOn = choice == selected
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isOn) 2.dp else 1.dp,
                        color = if (isOn) Palette.Text else Palette.Hairline,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(choice) },
                contentAlignment = Alignment.Center,
            ) {
                if (isOn) {
                    Text(
                        text = "✓",
                        color = if (choice == AccentChoice.MONO) Palette.Bg else Palette.Bg,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/** 只露前 6 位和后 4 位，中间打码。 */
internal fun maskKey(key: String): String {
    if (key.length <= 12) return "••••"
    return key.take(6) + "••••" + key.takeLast(4)
}
