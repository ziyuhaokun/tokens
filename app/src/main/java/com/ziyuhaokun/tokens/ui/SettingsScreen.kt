package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    /** 每保存成功一次 +1；变了就收起表单。 */
    saveCount: Int,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentChoice) -> Unit,
    onFontScale: (FontScaleChoice) -> Unit,
    onMotion: (Boolean) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onDismissCredentials: () -> Unit,
    onSignOut: () -> Unit,
    onClearCache: () -> Unit,
    onRefreshAll: () -> Unit,
) {
    var showCredentials by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }

    // 只在验证通过后收起表单；失败时表单留着，错误就显示在里面。
    val initialSaveCount = remember { saveCount }
    LaunchedEffect(saveCount) {
        if (saveCount != initialSaveCount) showCredentials = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TokensSpace.Page),
    ) {
        Spacer(Modifier.height(8.dp))

        // 返回 + 标题。TextAction 自带 10dp 横向内边距，往左挪回来才对得上标题的左边缘。
        TextAction(
            text = "← 返回",
            onClick = onBack,
            modifier = Modifier.offset(x = (-10).dp),
        )
        Spacer(Modifier.height(6.dp))
        Text("设置", color = Palette.Text, fontSize = TokensType.Title, fontWeight = FontWeight.Light)
        Text(
            text = settings?.apiKey?.let { "API Key 已配置 · ${maskKey(it)}" } ?: "未配置 API Key",
            color = Palette.Dim,
            fontSize = TokensType.Caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(TokensSpace.CardGap))

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

        Spacer(Modifier.height(TokensSpace.CardGap))

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
                color = Palette.Dim,
                fontSize = TokensType.Caption,
                lineHeight = TokensType.Caption * 1.45f,
            )
        }

        Spacer(Modifier.height(TokensSpace.CardGap))

        // 数据
        SettingsGroup("数据") {
            SettingsRow("服务器") {
                Text(
                    text = settings?.baseUrl ?: "—",
                    color = Palette.TextSoft,
                    fontSize = TokensType.Value,
                    fontFamily = NumericFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HairlineDivider()
            SettingsRow("API Key") {
                Text(
                    text = settings?.apiKey?.let { maskKey(it) } ?: "未配置",
                    color = Palette.Dim,
                    fontSize = TokensType.Value,
                    fontFamily = NumericFont,
                    maxLines = 1,
                )
            }
            HairlineDivider()
            ActionRow("更新 Key 或服务器地址") {
                if (showCredentials) onDismissCredentials()
                showCredentials = !showCredentials
            }
            if (showCredentials) {
                Spacer(Modifier.height(10.dp))
                CredentialsForm(
                    initialKey = "",
                    initialUrl = settings?.baseUrl ?: "https://vibecafe.ai",
                    saving = saving,
                    error = saveError,
                    saveLabel = "验证并保存",
                    allowBlankKey = true,
                    onSave = onSaveCredentials,
                    onCancel = {
                        showCredentials = false
                        onDismissCredentials()
                    },
                )
            }
            HairlineDivider()
            ActionRow(
                label = if (cleared) "缓存已清除" else "清除本地缓存",
                valueColor = if (cleared) Palette.Ok else null,
            ) {
                // onClearCache 内部会在清完之后自己触发刷新。
                onClearCache()
                cleared = true
            }
            HairlineDivider()
            ActionRow("立即刷新") { onRefreshAll() }
        }

        Spacer(Modifier.height(TokensSpace.CardGap))

        // 关于
        SettingsGroup("关于") {
            SettingsRow("版本") {
                Text(
                    text = "v$versionName",
                    color = Palette.TextSoft,
                    fontSize = TokensType.Value,
                    fontFamily = NumericFont,
                )
            }
            HairlineDivider()
            ActionRow("退出登录（清除 Key）", valueColor = Palette.Warn) { onSignOut() }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            color = Palette.Dim,
            fontSize = TokensType.Caption,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        SoftCard(contentPadding = TokensSpace.CardPaddingCompact) { content() }
    }
}

/**
 * 左标签右控件。大字号时控件换成标签下方一行：
 * 「文字大小」那种四个选项的分段选择器在窄屏上放不进右侧。
 */
@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    val stacked = isLargeText()
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .padding(vertical = 8.dp)

    if (stacked) {
        Column(modifier = rowModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsRowLabel(label)
            content()
        }
    } else {
        Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
            SettingsRowLabel(label, Modifier.weight(1f))
            content()
        }
    }
}

@Composable
private fun SettingsRowLabel(label: String, modifier: Modifier = Modifier) {
    Text(text = label, color = Palette.TextSoft, fontSize = TokensType.Body, modifier = modifier)
}

@Composable
private fun ActionRow(label: String, valueColor: Color? = null, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, color = valueColor ?: Palette.TextSoft, fontSize = TokensType.Body)
    }
}

/** 一行分段选择器，选中项用强调色描边。每段点击高度 36dp。 */
@Composable
private fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val isOn = value == selected
            val shape = RoundedCornerShape(9.dp)
            Box(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(shape)
                    .background(if (isOn) Palette.Field else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isOn) Palette.Accent.copy(alpha = 0.55f) else Color.Transparent,
                        shape = shape,
                    )
                    .semantics { this.selected = isOn }
                    .clickable(role = Role.Tab) { if (!isOn) onSelect(value) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isOn) Palette.Text else Palette.Dim,
                    fontSize = TokensType.Label,
                    fontWeight = if (isOn) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 一排色点：26dp 的色块放在 44dp 的点击区域里（水波纹裁成圆形）。
 * 选中态不在色块上盖符号，改成外面套一圈 2dp 的正文色细环——
 * 任何色块上都看得见，也不用担心「✓」跟底色撞色。
 */
@Composable
private fun AccentRow(selected: AccentChoice, onSelect: (AccentChoice) -> Unit) {
    val isDark = LocalTokensColors.current.isDark
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentChoice.entries.forEach { choice ->
            val isOn = choice == selected
            Box(
                modifier = Modifier
                    .size(TokensSpace.MinTouch)
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = choice.label
                        this.selected = isOn
                    }
                    .clickable(role = Role.Tab) { onSelect(choice) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(accentColor(choice, isDark))
                        .border(width = 1.dp, color = Palette.Hairline, shape = CircleShape)
                        .then(
                            if (isOn) Modifier.border(2.dp, Palette.Text, CircleShape) else Modifier
                        ),
                )
            }
        }
    }
}

/** 只露前 6 位和后 4 位，中间打码。 */
internal fun maskKey(key: String): String {
    if (key.length <= 12) return "••••"
    return key.take(6) + "••••" + key.takeLast(4)
}
