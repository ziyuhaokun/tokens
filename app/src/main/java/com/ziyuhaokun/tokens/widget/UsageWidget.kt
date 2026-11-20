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
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
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

private val Bg = Color(0xFF12151A)
private val TextPrimary = Color(0xFFE8EBF0)
private val TextMuted = Color(0xFF858E9D)
private val Accent = Color(0xFF7AA2FF)
private val CostColor = Color(0xFFF0B429)
private val BarColor = Color(0xFF2E3A56)

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
                .cornerRadius(18.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "云端用量",
                    style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = s.handle ?: "—",
                    style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = refreshHint(s),
                    style = TextStyle(color = ColorProvider(Accent), fontSize = 11.sp),
                )
            }

            Spacer(GlanceModifier.height(10.dp))

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Metric("今日", money(s.todayCost), CostColor, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(12.dp))
                Metric("7 天", money(s.weekCost), CostColor, GlanceModifier.defaultWeight())
            }

            Spacer(GlanceModifier.height(8.dp))

            Text(
                text = if (s.configured) "7 天 ${tokens(s.weekTokens)} tokens" else (s.message ?: ""),
                style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp),
                maxLines = 1,
            )

            Spacer(GlanceModifier.height(8.dp))
            Bars(s.trend)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        Text(text = label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp))
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = value,
            style = TextStyle(color = ColorProvider(color), fontSize = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
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
            Box(modifier = GlanceModifier.fillMaxWidth().height(2.dp).background(ColorProvider(BarColor))) {}
        } else {
            values.forEach { value ->
                val heightDp = (2 + (value / max) * 22).toInt().coerceIn(2, 24)
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 1.dp)
                        .height(heightDp.dp)
                        .cornerRadius(2.dp)
                        .background(ColorProvider(if (value > 0) Accent else BarColor)),
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

private fun money(value: Double): String = "$" + String.format(Locale.US, "%.2f", value)

private fun tokens(value: Long): String = when {
    value >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1e9)
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1e6)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1e3)
    else -> value.toString()
}
