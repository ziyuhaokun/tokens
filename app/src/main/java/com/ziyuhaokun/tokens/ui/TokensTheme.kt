package com.ziyuhaokun.tokens.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFF0A0B0D)
    val Surface = Color(0xFF111317)
    val SurfaceHigh = Color(0xFF15181D)
    val Line = Color(0xFF22262E)
    val Text = Color(0xFFE8EBF0)
    val Muted = Color(0xFF858E9D)
    val Dim = Color(0xFF5D646F)
    val Accent = Color(0xFF7AA2FF)
    val Cost = Color(0xFFF0B429)
    val Ok = Color(0xFF47C98D)
    val Danger = Color(0xFFFF8A8A)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.Bg,
    secondary = Palette.Cost,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
    error = Palette.Danger,
)

@Composable
fun TokensTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
