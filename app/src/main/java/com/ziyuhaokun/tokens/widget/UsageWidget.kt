package com.ziyuhaokun.tokens.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ziyuhaokun.tokens.AppContainer
import com.ziyuhaokun.tokens.data.AccentChoice
import com.ziyuhaokun.tokens.data.ThemeMode
import com.ziyuhaokun.tokens.ui.DarkTokensColors
import com.ziyuhaokun.tokens.data.NumberFormats
import com.ziyuhaokun.tokens.ui.LightTokensColors
import com.ziyuhaokun.tokens.ui.MainActivity
import com.ziyuhaokun.tokens.ui.accentColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 小组件里不放小于 11sp 的字，跟 App 内的字号阶梯一致。
private val LabelSize = 11.sp
private val MoneySize = 26.sp
private val MoneyDecimalSize = 13.sp

/**
 * 小组件用的颜色角色。跟 App 里一样只用角色名，不写死色值。
 *
 * 深色 / 浅色模式直接取对应那一套；跟随系统时用 Glance 的 day/night ColorProvider，
 * 系统切换深浅色后桌面重绘就会跟着变。
 */
private class WidgetPalette(
    val bg: ColorProvider,
    val primary: ColorProvider,
    val secondary: ColorProvider,
    val tertiary: ColorProvider,
    val barActive: ColorProvider,
    val barIdle: ColorProvider,
)

private fun widgetPalette(mode: ThemeMode, accent: AccentChoice): WidgetPalette {
    fun pick(light: Color, dark: Color): ColorProvider = when (mode) {
        ThemeMode.DARK -> ColorProvider(dark)
        ThemeMode.LIGHT -> ColorProvider(light)
        ThemeMode.SYSTEM -> DayNightColorProvider(day = light, night = dark)
    }

    return WidgetPalette(
        bg = pick(LightTokensColors.cardBottom, DarkTokensColors.cardBottom),
        primary = pick(LightTokensColors.text, DarkTokensColors.text),
        secondary = pick(LightTokensColors.muted, DarkTokensColors.muted),
        tertiary = pick(LightTokensColors.dim, DarkTokensColors.dim),
        // 柱子只点亮有数据的那根：强调色压到 0.9 透明，没数据的用发丝线。
        barActive = pick(
            accentColor(accent, isDark = false).copy(alpha = 0.9f),
            accentColor(accent, isDark = true).copy(alpha = 0.9f),
        ),
        barIdle = pick(LightTokensColors.hairline, DarkTokensColors.hairline),
    )
}

class UsageWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = AppContainer.get(context).repository
        // 先读一次当首帧，之后设置 / 缓存一变就自动重读重画。
        val initial = WidgetData.read(repository)
        provideContent {
            val flow = remember { WidgetData.observe(repository) }
            val snapshot by flow.collectAsState(initial = initial)
            Content(snapshot)
        }
    }
}

@Composable
private fun Content(s: WidgetSnapshot) {
    val palette = widgetPalette(s.themeMode, s.accent)
    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(palette.bg)
                .cornerRadius(22.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tokens",
                    style = TextStyle(color = palette.secondary, fontSize = LabelSize),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = s.handle ?: "—",
                    style = TextStyle(color = palette.tertiary, fontSize = LabelSize),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                // 只有配了 key 才给刷新；未配置时点哪里都是进 App。
                Text(
                    text = refreshHint(s),
                    style = TextStyle(color = palette.secondary, fontSize = LabelSize),
                    modifier = if (s.configured) {
                        GlanceModifier
                            .clickable(actionRunCallback<RefreshAction>())
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    } else {
                        GlanceModifier
                    },
                )
            }

            Spacer(GlanceModifier.height(12.dp))

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                MoneyBlock("今日", s.todayCost, s.formats, palette, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(14.dp))
                MoneyBlock("7 天", s.weekCost, s.formats, palette, GlanceModifier.defaultWeight())
            }

            Spacer(GlanceModifier.height(6.dp))

            Text(
                text = if (s.configured) "${s.weekTokens?.let { s.formats.tokens(it) } ?: "—"} tokens · 7 天" else (s.message ?: ""),
                style = TextStyle(color = palette.tertiary, fontSize = LabelSize),
                maxLines = 1,
            )

            Spacer(GlanceModifier.height(10.dp))
            Bars(s.trend, palette)
        }
    }
}

@Composable
private fun MoneyBlock(
    label: String,
    value: Double?,
    formats: NumberFormats,
    palette: WidgetPalette,
    modifier: GlanceModifier,
) {
    // 没配置时显示占位符，不显示 $0.00 这种像真数字的东西。
    // "$" 单独一段小字：跟 App 里 BigMoney 一样「小号 $ + 大号整数 + 小号小数」。
    val (integer, decimals) = if (value == null) "—" to "" else formats.moneyParts(value)
    val prefix = integer.takeWhile { !it.isDigit() && it != '-' && it != '—' }
    val currency = prefix.ifEmpty { null }
    val digits = integer.drop(prefix.length)
    Column(modifier = modifier) {
        Text(text = label, style = TextStyle(color = palette.tertiary, fontSize = LabelSize))
        Spacer(GlanceModifier.height(2.dp))
        // Glance 没有 alignByBaseline，只能按底边对齐，视觉上和基线对齐基本一致。
        Row(verticalAlignment = Alignment.Bottom) {
            if (currency != null) {
                Text(
                    text = currency,
                    style = TextStyle(
                        color = palette.secondary,
                        fontSize = MoneyDecimalSize,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
            Text(
                text = digits,
                style = TextStyle(
                    color = palette.primary,
                    fontSize = MoneySize,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
            Text(
                text = decimals,
                style = TextStyle(
                    color = palette.secondary,
                    fontSize = MoneyDecimalSize,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Bars(values: List<Double>, palette: WidgetPalette) {
    val max = (values.maxOrNull() ?: 0.0).takeIf { it > 0.0 } ?: 1.0
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (values.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.barIdle),
            ) {}
        } else {
            values.forEach { value ->
                val heightDp = (2 + (value / max) * 24).toInt().coerceIn(2, 26)
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 2.dp)
                        .height(heightDp.dp)
                        .cornerRadius(3.dp)
                        .background(if (value > 0) palette.barActive else palette.barIdle),
                ) {}
            }
        }
    }
}

private fun refreshHint(s: WidgetSnapshot): String {
    if (!s.configured) return "去配置"
    val at = s.updatedAt ?: return "刷新"
    val text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
    return "刷新 · $text"
}
