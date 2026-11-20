package com.ziyuhaokun.tokens.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import com.ziyuhaokun.tokens.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 和 App 内一致的单色系
private val Bg = Color(0xFF141619)
private val TextPrimary = Color(0xFFF2F3F5)
private val TextSoft = Color(0xFFCFD3D9)
private val TextMuted = Color(0xFF9AA0A9)
private val TextDim = Color(0xFF6B7078)
private val BarActive = Color(0xFFCFD3D9)
private val BarIdle = Color(0xFF2A2D33)

class UsageWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.read(AppContainer.get(context).settings)
        provideContent { Content(snapshot) }
    }
}

@Composable
private fun Content(s: WidgetSnapshot) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Bg))
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
                    style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = s.handle ?: "—",
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = refreshHint(s),
                    style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp),
                )
            }

            Spacer(GlanceModifier.height(12.dp))

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                MoneyBlock("今日", s.todayCost, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(14.dp))
                MoneyBlock("7 天", s.weekCost, GlanceModifier.defaultWeight())
            }

            Spacer(GlanceModifier.height(6.dp))

            Text(
                text = if (s.configured) "${tokens(s.weekTokens)} tokens · 7 天" else (s.message ?: ""),
                style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp),
                maxLines = 1,
            )

            Spacer(GlanceModifier.height(10.dp))
            Bars(s.trend)
        }
    }
}

@Composable
private fun MoneyBlock(label: String, value: Double, modifier: GlanceModifier) {
    val text = String.format(Locale.US, "%.2f", value)
    val dot = text.indexOf('.')
    val integer = "$" + text.substring(0, dot)
    val decimals = text.substring(dot)
    Column(modifier = modifier) {
        Text(text = label, style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp))
        Spacer(GlanceModifier.height(2.dp))
        Row {
            Text(
                text = integer,
                style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 24.sp, fontWeight = FontWeight.Normal),
                maxLines = 1,
            )
            Text(
                text = decimals,
                style = TextStyle(color = ColorProvider(TextMuted), fontSize = 13.sp, fontWeight = FontWeight.Normal),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Bars(values: List<Double>) {
    val max = (values.maxOrNull() ?: 0.0).takeIf { it > 0.0 } ?: 1.0
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(26.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (values.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ColorProvider(BarIdle)),
            ) {}
        } else {
            values.forEach { value ->
                val heightDp = (2 + (value / max) * 22).toInt().coerceIn(2, 24)
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 1.dp)
                        .height(heightDp.dp)
                        .cornerRadius(2.dp)
                        .background(ColorProvider(if (value > 0) BarActive.copy(alpha = 0.75f) else BarIdle)),
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

private fun tokens(value: Long): String = when {
    value >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1e9)
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1e6)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1e3)
    else -> value.toString()
}
