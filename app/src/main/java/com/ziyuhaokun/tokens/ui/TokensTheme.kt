package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 参考 Vibe Usage 桌面端：近黑背景 + 略亮的卡片，几乎全黑白，
 * 颜色只留给「告警」和状态点。
 */
object Palette {
    val Bg = Color(0xFF0B0C0E)
    val CardTop = Color(0xFF1A1C21)
    val CardBottom = Color(0xFF141619)
    val Field = Color(0xFF17191D)
    val Hairline = Color(0xFF26292E)
    val HairlineSoft = Color(0xFF1F2226)

    val Text = Color(0xFFF2F3F5)
    val TextSoft = Color(0xFFCFD3D9)
    val Muted = Color(0xFF9AA0A9)
    val Dim = Color(0xFF6B7078)
    val Faint = Color(0xFF3A3E45)

    val Ok = Color(0xFF9BE6A8)
    val Warn = Color(0xFFFF7A70)
}

private val scheme = darkColorScheme(
    primary = Palette.Text,
    onPrimary = Palette.Bg,
    secondary = Palette.Muted,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.CardBottom,
    onSurface = Palette.Text,
    surfaceVariant = Palette.CardTop,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Hairline,
    error = Palette.Warn,
)

@Composable
fun TokensTheme(content: @Composable () -> Unit) {
    // 这一版只有深色，仍然是单色系：不做浅色主题的分支。
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
