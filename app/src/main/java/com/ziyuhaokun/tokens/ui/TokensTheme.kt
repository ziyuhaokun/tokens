package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.AccentChoice
import com.ziyuhaokun.tokens.data.FontScaleChoice
import com.ziyuhaokun.tokens.data.ThemeMode

/**
 * 一套颜色角色。深色/浅色两套值，界面里只用角色名，不写死色值。
 *
 * 设计语言没变：单色系、近黑/近白的底、发丝分隔；唯一允许上色的是 accent，
 * 默认等于正文色（也就是纯黑白），只在用户主动选强调色时才变彩。
 */
@Immutable
data class TokensColors(
    val bg: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val field: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    val text: Color,
    val textSoft: Color,
    val muted: Color,
    val dim: Color,
    val faint: Color,
    val ok: Color,
    val warn: Color,
    val accent: Color,
    val isDark: Boolean,
)

/** 深色：近黑底 + 略亮卡片，颜色只留给告警和强调色。 */
val DarkTokensColors = TokensColors(
    bg = Color(0xFF0B0C0E),
    cardTop = Color(0xFF1A1C21),
    cardBottom = Color(0xFF141619),
    field = Color(0xFF17191D),
    hairline = Color(0xFF26292E),
    hairlineSoft = Color(0xFF1F2226),
    text = Color(0xFFF2F3F5),
    textSoft = Color(0xFFCFD3D9),
    // muted / dim 是文字色，在卡片底上对比度分别约 7.4:1 / 5.3:1。
    muted = Color(0xFFA6ABB3),
    dim = Color(0xFF8A9099),
    // faint 只给线条、空格、未点亮的刻度用，不许拿来写字（对比度只有 ~1.6:1）。
    faint = Color(0xFF3A3E45),
    ok = Color(0xFF9BE6A8),
    warn = Color(0xFFFF7A70),
    accent = Color(0xFFF2F3F5),
    isDark = true,
)

/** 浅色：同一个结构的白底黑字版本，不是另做一套 UI。 */
val LightTokensColors = TokensColors(
    bg = Color(0xFFF6F6F7),
    cardTop = Color(0xFFFFFFFF),
    cardBottom = Color(0xFFF1F2F4),
    field = Color(0xFFEDEEF1),
    hairline = Color(0xFFD8DADF),
    hairlineSoft = Color(0xFFE5E7EA),
    text = Color(0xFF101215),
    textSoft = Color(0xFF2A2E34),
    // 在 #F1F2F4 卡片底上 muted / dim 约 7:1 / 4.8:1。
    muted = Color(0xFF4E545C),
    dim = Color(0xFF646A73),
    // 同上：只用于非文字元素。
    faint = Color(0xFFC3C7CD),
    ok = Color(0xFF2C7F4B),
    warn = Color(0xFFC0392B),
    accent = Color(0xFF101215),
    isDark = false,
)

val LocalTokensColors = staticCompositionLocalOf { DarkTokensColors }

/** 动态效果开关：false 时不播放入场/切换动画。 */
val LocalMotionEnabled = staticCompositionLocalOf { true }

/** 用户选的字号倍率。布局靠它决定要不要换行/缩字号，避免大字号挤成一团。 */
val LocalFontScale = staticCompositionLocalOf { 1f }

/**
 * 字号阶梯。界面里只用这些，不再到处写死 sp。
 * 任何文字不小于 [Micro]（11sp）—— 之前 7~10sp 的字在手机上基本看不清。
 */
object TokensType {
    /** 首页 / 年度的大金额。 */
    val Display = 46.sp
    /** 页面标题（「设置」）、次级大数字。 */
    val Title = 22.sp
    /** App 名、页头。 */
    val Header = 19.sp
    /** 卡片标题（「今天」「按模型」）。 */
    val CardTitle = 15.sp
    /** 正文、列表名称、可点的文字链接。 */
    val Body = 14.sp
    /** 结构化数字（等宽）。 */
    val Value = 14.sp
    /** 数字旁的标签（「Token」「活跃」）。 */
    val Label = 13.sp
    /** 次要说明、脚注。 */
    val Caption = 12.sp
    /** 最小号：坐标轴、图例、热力图刻度。 */
    val Micro = 11.sp
}

/** 间距与圆角。 */
object TokensSpace {
    val Page = 16.dp
    val CardGap = 14.dp
    val CardPadding = 20.dp
    val CardPaddingCompact = 16.dp
    val CardRadius = 24.dp
    /** 可点击元素的最小点击高度。 */
    val MinTouch = 44.dp
}

/**
 * 数字字体：等宽，跟桌面端「65.5M / 3h 12m / 9:00」那种排版一致。
 * 直接用打包进来的 JetBrains Mono，见 [ReceiptFont]。
 */
val NumericFont: FontFamily = ReceiptFont

/** 大字号时把多列改成单列。 */
@Composable
fun isLargeText(): Boolean = LocalFontScale.current > 1.05f

/**
 * 颜色入口。保留 `Palette.Text` 这种写法，内部从主题取，
 * 所以 100 多处调用点不用改；代价是只能在 Composable 里读。
 */
object Palette {
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.bg
    val CardTop: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.cardTop
    val CardBottom: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.cardBottom
    val Field: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.field
    val Hairline: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.hairline
    val HairlineSoft: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.hairlineSoft
    val Text: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.text
    val TextSoft: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.textSoft
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.muted
    val Dim: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.dim
    val Faint: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.faint
    val Ok: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.ok
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.warn
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalTokensColors.current.accent
}

/** 强调色只有这几档；单色 = 直接用正文色，等于关掉上色。 */
fun accentColor(choice: AccentChoice, isDark: Boolean): Color = when (choice) {
    AccentChoice.MONO -> if (isDark) Color(0xFFF2F3F5) else Color(0xFF101215)
    AccentChoice.BLUE -> if (isDark) Color(0xFF7FA6FF) else Color(0xFF2C5FD0)
    AccentChoice.PURPLE -> if (isDark) Color(0xFFB49CFF) else Color(0xFF6A45C9)
    AccentChoice.TEAL -> if (isDark) Color(0xFF74D9CE) else Color(0xFF167F76)
    AccentChoice.ORANGE -> if (isDark) Color(0xFFFFB478) else Color(0xFFB45E15)
}

@Composable
fun TokensTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentChoice = AccentChoice.MONO,
    fontScale: FontScaleChoice = FontScaleChoice.STANDARD,
    motion: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val base = if (dark) DarkTokensColors else LightTokensColors
    val colors = base.copy(accent = accentColor(accent, dark))

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.bg,
            secondary = colors.muted,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.cardBottom,
            onSurface = colors.text,
            surfaceVariant = colors.cardTop,
            onSurfaceVariant = colors.muted,
            outline = colors.hairline,
            error = colors.warn,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.bg,
            secondary = colors.muted,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.cardBottom,
            onSurface = colors.text,
            surfaceVariant = colors.cardTop,
            onSurfaceVariant = colors.muted,
            outline = colors.hairline,
            error = colors.warn,
        )
    }

    // 文字大小走 density.fontScale：所有 sp 一起缩放，不用逐个改字号。
    val density = LocalDensity.current
    val scaled = Density(density.density, density.fontScale * fontScale.scale)

    CompositionLocalProvider(
        LocalTokensColors provides colors,
        LocalMotionEnabled provides motion,
        LocalFontScale provides fontScale.scale,
        LocalDensity provides scaled,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
