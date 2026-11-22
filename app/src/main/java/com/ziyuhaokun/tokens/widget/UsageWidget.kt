package com.ziyuhaokun.tokens.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.ContentScale
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ziyuhaokun.tokens.AppContainer
import com.ziyuhaokun.tokens.data.AccentChoice
import com.ziyuhaokun.tokens.data.NumberFormats
import com.ziyuhaokun.tokens.data.ThemeMode
import com.ziyuhaokun.tokens.ui.DarkTokensColors
import com.ziyuhaokun.tokens.ui.LightTokensColors
import com.ziyuhaokun.tokens.ui.MainActivity
import com.ziyuhaokun.tokens.ui.accentColor
import com.ziyuhaokun.tokens.ui.drawUsageArea
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// 小组件里不放小于 11sp 的字，跟 App 内的字号阶梯一致。
private val LabelSize = 11.sp
private val ToolSize = 13.sp

/** 内容离卡片边缘至少 14dp：小米 HyperOS 规范要求，同时给桌面的圆角留出安全区。 */
private val CardPadding = 16.dp
private val SmallCardPadding = 14.dp

/** Android 12 以下没有 system_app_widget_background_radius，只能给一个固定圆角。 */
private val FallbackCornerRadius = 16.dp

/**
 * 尺寸档位。三个尺寸的小组件共用同一个 [UsageWidget]（桌面 updateAll 时按实例各自渲染），
 * 所以档位只能从实际尺寸判断，不能靠构造参数。
 */
private enum class WidgetSizeClass { SMALL, MEDIUM, LARGE }

private const val EmptyTrendHint = "暂无 7 天数据 · 点右上角刷新"

/** 估算行高用：Glance 里文字按 SP 交给系统缩放，行高约为字号的这个倍数。 */
private const val LineHeightFactor = 1.4f

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

    // Android 15 桌面生成预览时按这三个尺寸各画一张，跟真实的 2x2 / 4x2 / 4x4 对齐。
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(
        setOf(DpSize(150.dp, 150.dp), DpSize(320.dp, 150.dp), DpSize(320.dp, 320.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = AppContainer.get(context)
        val repository = container.repository
        val initial = WidgetData.read(repository)
        provideContent {
            val flow = remember { WidgetData.observe(repository) }
            val snapshot by flow.collectAsState(initial = initial)
            val refreshing by container.widgetSync.refreshing.collectAsState()
            Content(if (refreshing) snapshot.copy(refreshing = true) else snapshot)
        }
    }

    /**
     * Android 15+ 桌面「预览」用固定样例数据，不能出现真实账号和真实金额。
     * updatedAt 给 null，右上角就显示「刷新」而不是某个时刻。
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { Content(WidgetSnapshot.PREVIEW) }
    }
}

@Composable
private fun Content(s: WidgetSnapshot) {
    val palette = widgetPalette(s.themeMode, s.accent)
    val sizeClass = currentSizeClass()
    val cardHeight = LocalSize.current.height.value
    val fontScale = systemFontScale()
    val compact = sizeClass != WidgetSizeClass.LARGE && cardHeight < 145f * fontScale
    val padding = when {
        compact -> 12.dp
        sizeClass == WidgetSizeClass.SMALL -> SmallCardPadding
        else -> CardPadding
    }
    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                // 铺满 + 不透明底 + 系统背景标记，桌面才会按统一圆角裁切（HyperOS 要求）。
                .appWidgetBackground()
                .background(palette.bg)
                .then(cardCorner())
                .padding(padding)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (!s.configured) {
                NotConfiguredContent(s, palette)
            } else if (compact) {
                CompactContent(s, palette, sizeClass)
            } else {
                val chart = chartHeight(sizeClass, cardHeight, fontScale, padding.value)
                when (sizeClass) {
                    WidgetSizeClass.SMALL -> SmallContent(s, palette, chart)
                    WidgetSizeClass.MEDIUM -> MediumContent(s, palette, chart)
                    WidgetSizeClass.LARGE -> LargeContent(s, palette, chart)
                }
            }
        }
    }
}

/** 宽度不到 200dp 就是 2x2；高度过了 200dp 才是 4x4，剩下的按 4x2 处理。 */
@Composable
private fun currentSizeClass(): WidgetSizeClass {
    val size = LocalSize.current
    return when {
        size.width < 200.dp -> WidgetSizeClass.SMALL
        size.height >= 200.dp -> WidgetSizeClass.LARGE
        else -> WidgetSizeClass.MEDIUM
    }
}

/** 系统字号倍率：文字按 SP 渲染，估高度时要把这个倍数算进去，不然大字号下柱子会顶到字上。 */
@Composable
private fun systemFontScale(): Float {
    val scale = LocalContext.current.resources.configuration.fontScale
    return if (scale > 0f) scale else 1f
}

private fun cardCorner(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // 让桌面用系统统一圆角，跟别的卡片对齐。
        GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        GlanceModifier.cornerRadius(FallbackCornerRadius)
    }

// ---------------------------------------------------------------- 三个尺寸的排版

/** 桌面给的高度偏小时，先保证金额、摘要和刷新入口完整。 */
@Composable
private fun ColumnScope.CompactContent(s: WidgetSnapshot, palette: WidgetPalette, sizeClass: WidgetSizeClass) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Tokens", style = TextStyle(color = palette.secondary, fontSize = LabelSize), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        RefreshHint(s, palette, compact = true)
    }
    Spacer(GlanceModifier.defaultWeight())
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        MoneyLine("今日", s.todayCost, s.formats, palette, sizeClass, GlanceModifier.defaultWeight(), compact = true)
        if (sizeClass == WidgetSizeClass.MEDIUM) {
            Spacer(GlanceModifier.width(10.dp))
            MoneyLine("7 天", s.weekCost, s.formats, palette, sizeClass, GlanceModifier.defaultWeight(), compact = true)
        }
    }
    Spacer(GlanceModifier.defaultWeight())
    if (sizeClass == WidgetSizeClass.SMALL) {
        Text(
            text = "7 天 ${s.weekCost?.let(s.formats::money) ?: "—"}",
            style = TextStyle(color = palette.tertiary, fontSize = LabelSize),
            maxLines = 1,
        )
    } else {
        TokenLine(s, palette)
    }
}

/** 2x2：标题 + 今日大金额 + 迷你柱状图 + 「7 天 xx」脚注。 */
@Composable
private fun ColumnScope.SmallContent(s: WidgetSnapshot, palette: WidgetPalette, chartHeight: Float) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Tokens", style = TextStyle(color = palette.secondary, fontSize = LabelSize), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        RefreshHint(s, palette)
    }

    Spacer(GlanceModifier.height(8.dp))

    MoneyLine(
        label = "今日",
        value = s.todayCost,
        formats = s.formats,
        palette = palette,
        sizeClass = WidgetSizeClass.SMALL,
    )

    Spacer(GlanceModifier.height(6.dp))
    Bars(
        values = s.trend,
        palette = palette,
        maxBarHeight = chartHeight,
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
    )
    Spacer(GlanceModifier.height(6.dp))

    Text(
        text = "7 天 ${s.weekCost?.let { s.formats.money(it) } ?: "—"}",
        style = TextStyle(color = palette.tertiary, fontSize = LabelSize),
        maxLines = 1,
    )
}

/** 4x2：今日 / 7 天两个金额块 + token 摘要 + 柱状图，柱子吃掉剩下的高度。 */
@Composable
private fun ColumnScope.MediumContent(s: WidgetSnapshot, palette: WidgetPalette, chartHeight: Float) {
    Header(s, palette)

    Spacer(GlanceModifier.height(12.dp))

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        MoneyLine(
            label = "今日",
            value = s.todayCost,
            formats = s.formats,
            palette = palette,
            sizeClass = WidgetSizeClass.MEDIUM,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(14.dp))
        MoneyLine(
            label = "7 天",
            value = s.weekCost,
            formats = s.formats,
            palette = palette,
            sizeClass = WidgetSizeClass.MEDIUM,
            modifier = GlanceModifier.defaultWeight(),
        )
    }

    Spacer(GlanceModifier.height(6.dp))
    TokenLine(s, palette)
    Spacer(GlanceModifier.height(10.dp))
    Bars(
        values = s.trend,
        palette = palette,
        maxBarHeight = chartHeight,
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
    )
}

/** 4×4：近 7 天费用面积图、金额摘要和工具费用前三名。 */
@Composable
private fun ColumnScope.LargeContent(s: WidgetSnapshot, palette: WidgetPalette, chartHeight: Float) {
    Header(s, palette)

    Spacer(GlanceModifier.height(12.dp))

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        MoneyLine(
            label = "今日",
            value = s.todayCost,
            formats = s.formats,
            palette = palette,
            sizeClass = WidgetSizeClass.LARGE,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(14.dp))
        MoneyLine(
            label = "7 天",
            value = s.weekCost,
            formats = s.formats,
            palette = palette,
            sizeClass = WidgetSizeClass.LARGE,
            modifier = GlanceModifier.defaultWeight(),
        )
    }

    Spacer(GlanceModifier.height(6.dp))
    TokenLine(s, palette)
    Spacer(GlanceModifier.height(10.dp))
    CostAreaChart(
        values = s.trend,
        formats = s.formats,
        palette = palette,
        chartHeight = chartHeight,
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
    )
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (s.trend.isNotEmpty()) {
            Spacer(GlanceModifier.height(4.dp))
            DayLabels(palette)
        }
        Spacer(GlanceModifier.height(10.dp))
        ToolList(s.topTools, s.formats, palette)
    }
}

// ---------------------------------------------------------------- 复用的小块

/** 标题行：Tokens + handle（占满中间剩余宽度，长了就裁）+ 右上角刷新。 */
@Composable
private fun Header(s: WidgetSnapshot, palette: WidgetPalette) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Tokens", style = TextStyle(color = palette.secondary, fontSize = LabelSize), maxLines = 1)
        Spacer(GlanceModifier.width(8.dp))
        // Glance 1.2 没有 ellipsize，只能 maxLines = 1 + 权重宽度，超出的部分被裁掉，
        // 好处是再长的 handle 也挤不掉右边的刷新按钮。
        Text(
            text = s.handle ?: "—",
            style = TextStyle(color = palette.tertiary, fontSize = LabelSize),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        RefreshHint(s, palette)
    }
}

@Composable
private fun RefreshHint(s: WidgetSnapshot, palette: WidgetPalette, compact: Boolean = false) {
    Text(
        text = refreshHint(s),
        style = TextStyle(color = palette.secondary, fontSize = LabelSize),
        maxLines = 1,
        modifier = GlanceModifier
            .clickable(actionRunCallback<RefreshAction>())
            .padding(horizontal = 8.dp, vertical = if (compact) 2.dp else 6.dp),
    )
}

/**
 * 一行大金额：小号货币符号 + 大号整数 + 小号小数，跟 App 里的 BigMoney 一致。
 * value 为 null（没配 key / 还没拉到数据）时显示「—」，不显示 $0.00 这种像真数字的东西。
 */
@Composable
private fun MoneyLine(
    label: String,
    value: Double?,
    formats: NumberFormats,
    palette: WidgetPalette,
    sizeClass: WidgetSizeClass,
    modifier: GlanceModifier = GlanceModifier,
    compact: Boolean = false,
) {
    val (integer, decimals) = if (value == null) "—" to "" else formats.moneyParts(value)
    // 开头的非数字部分就是货币符号（负号 / 「—」留在数字里）。
    val prefix = integer.takeWhile { !it.isDigit() && it != '-' && it != '—' }
    val digits = integer.drop(prefix.length)
    val preferredSize = moneyDigitsSize(integer.length, sizeClass)
    val digitsSize = if (compact) minOf(preferredSize.value, 22f).sp else preferredSize
    val fractionSize = moneyFractionSize(digitsSize)
    Column(modifier = modifier) {
        Text(text = label, style = TextStyle(color = palette.tertiary, fontSize = LabelSize), maxLines = 1)
        Spacer(GlanceModifier.height(2.dp))
        // Glance 没有 alignByBaseline，只能按底边对齐，视觉上和基线对齐基本一致。
        Row(verticalAlignment = Alignment.Bottom) {
            if (prefix.isNotEmpty()) {
                Text(
                    text = prefix,
                    style = TextStyle(color = palette.secondary, fontSize = fractionSize),
                    maxLines = 1,
                )
            }
            Text(
                text = digits,
                style = TextStyle(color = palette.primary, fontSize = digitsSize),
                maxLines = 1,
            )
            if (decimals.isNotEmpty()) {
                Text(
                    text = decimals,
                    style = TextStyle(color = palette.secondary, fontSize = fractionSize),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TokenLine(s: WidgetSnapshot, palette: WidgetPalette) {
    Text(
        text = "${s.weekTokens?.let { s.formats.tokens(it) } ?: "—"} tokens · 7 天",
        style = TextStyle(color = palette.tertiary, fontSize = LabelSize),
        maxLines = 1,
    )
}

/**
 * 7 天迷你柱状图。
 *
 * Row 自己吃掉 Column 剩下的高度（4x6 / 5x6 网格下卡片高度差 10~20% 也不会顶出卡片），
 * 柱子高度按估算出来的图表高度换算，底边对齐。没有数据时这一块显示一句提示，不留空白线。
 */
@Composable
private fun Bars(
    values: List<Double>,
    palette: WidgetPalette,
    maxBarHeight: Float,
    modifier: GlanceModifier,
) {
    if (values.isEmpty()) {
        EmptyTrend(palette, modifier)
    } else {
        val max = (values.maxOrNull() ?: 0.0).takeIf { it > 0.0 } ?: 1.0
        Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
            values.forEach { value ->
                val barHeight = if (value <= 0.0) {
                    // 没数据的那天留一条发丝线，不然看起来像少画了一根。
                    2f
                } else {
                    ((value / max).toFloat() * maxBarHeight).coerceIn(2f, maxBarHeight.coerceAtLeast(2f))
                }
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 2.dp)
                        .height(barHeight.dp)
                        .cornerRadius(3.dp)
                        .background(if (value > 0.0) palette.barActive else palette.barIdle),
                ) {}
            }
        }
    }
}

@Composable
private fun EmptyTrend(palette: WidgetPalette, modifier: GlanceModifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = EmptyTrendHint,
            style = TextStyle(color = palette.tertiary, fontSize = LabelSize, textAlign = TextAlign.Center),
            maxLines = 2,
        )
    }
}

@Composable
private fun CostAreaChart(
    values: List<Double>,
    formats: NumberFormats,
    palette: WidgetPalette,
    chartHeight: Float,
    modifier: GlanceModifier,
) {
    if (values.isEmpty()) {
        EmptyTrend(palette, modifier)
        return
    }

    // 按屏幕真实密度出图：按 dp 出图再被桌面放大，在 520dpi 上会糊成一片。
    val density = LocalContext.current.resources.displayMetrics.density.coerceAtLeast(1f)
    val widthDp = (LocalSize.current.width - CardPadding * 2).value.coerceIn(160f, 384f)
    val heightDp = chartHeight.coerceIn(32f, 220f)
    val image = remember(values, widthDp, heightDp, density) {
        costAreaImage(values, widthDp, heightDp, density)
    }
    Image(
        provider = image,
        contentDescription = "近 7 天每日费用面积图，今天 ${formats.money(values.last())}",
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(palette.barActive),
    )
}

private fun costAreaImage(values: List<Double>, widthDp: Float, heightDp: Float, density: Float): ImageProvider {
    // 上限 1024px：再大桌面也缩回去，只是白白吃 RemoteViews 的内存。
    val scale = minOf(density, 1024f / widthDp)
    val width = (widthDp * scale).roundToInt()
    val height = (heightDp * scale).roundToInt()
    val bitmap = ImageBitmap(width, height)
    // 用真实密度绘制，1dp 的基线、2.5dp 的线宽和 4dp 的圆点才是屏幕上该有的粗细。
    CanvasDrawScope().draw(Density(scale), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        // 留出末点触底时的空间，颜色由桌面的深浅主题着色。
        inset(0f, 0f, 0f, 3f) {
            drawUsageArea(values, Color.Black, Color.Black.copy(alpha = 0.16f))
        }
    }
    return ImageProvider(bitmap.asAndroidBitmap())
}

/** 面积图两端的日期标签。 */
@Composable
private fun DayLabels(palette: WidgetPalette) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = trendStartLabel(),
            style = TextStyle(color = palette.tertiary, fontSize = LabelSize),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = "今天",
            style = TextStyle(color = palette.tertiary, fontSize = LabelSize),
            maxLines = 1,
        )
    }
}

/** 「按工具 · 7 天」前三名：左边名字占满剩余宽度，右边金额固定宽度对齐。 */
@Composable
private fun ToolList(rows: List<WidgetToolRow>, formats: NumberFormats, palette: WidgetPalette) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = "按工具 · 7 天",
            style = TextStyle(color = palette.tertiary, fontSize = LabelSize),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        if (rows.isEmpty()) {
            Text(text = "—", style = TextStyle(color = palette.tertiary, fontSize = ToolSize), maxLines = 1)
        } else {
            rows.forEach { row ->
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.name,
                        style = TextStyle(color = palette.secondary, fontSize = ToolSize),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = formats.money(row.costUsd),
                        style = TextStyle(color = palette.primary, fontSize = ToolSize),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 没配 key：整张卡片只留「Tokens」和一句提示，点哪里都是进 App 填 key。 */
@Composable
private fun NotConfiguredContent(s: WidgetSnapshot, palette: WidgetPalette) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Tokens",
                style = TextStyle(color = palette.secondary, fontSize = LabelSize),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = s.message ?: "打开 App 填写 API Key",
                style = TextStyle(color = palette.primary, fontSize = ToolSize, textAlign = TextAlign.Center),
                maxLines = 2,
            )
        }
    }
}

// ---------------------------------------------------------------- 尺寸换算

/**
 * 金额字号：Glance 没有 autoSize，只能按整数段长度挑一档。
 * 6 个字符以内（$999.99 / ¥1,024）用最大号，再长就降档，
 * 保证「¥274,240.96」这种既不换行也不裁掉数字。
 */
private fun moneyDigitsSize(integerLength: Int, sizeClass: WidgetSizeClass): TextUnit {
    val (big, medium, compact) = when (sizeClass) {
        WidgetSizeClass.SMALL -> Triple(30.sp, 24.sp, 20.sp)
        WidgetSizeClass.MEDIUM -> Triple(28.sp, 24.sp, 20.sp)
        WidgetSizeClass.LARGE -> Triple(32.sp, 26.sp, 21.sp)
    }
    return when {
        integerLength <= 6 -> big
        integerLength <= 9 -> medium
        else -> compact
    }
}

/** 货币符号 / 小数跟着大数字等比缩小，但不低于 11sp。 */
private fun moneyFractionSize(digitsSize: TextUnit): TextUnit =
    maxOf(digitsSize.value * 0.5f, LabelSize.value).sp

/**
 * 柱状图能用多高（dp）。
 *
 * 卡片高度由桌面网格决定（4x6 / 5x6 差 10~20%），所以按 LocalSize 的实际高度
 * 减掉内边距和其余文案占的高度倒推。故意算小一点：算小了柱子矮一点、上面留白；
 * 算大了柱子会顶到上面的字。
 */
private fun chartHeight(
    sizeClass: WidgetSizeClass,
    cardHeight: Float,
    fontScale: Float,
    padding: Float,
): Float {
    fun line(sp: Float): Float = sp * fontScale * LineHeightFactor
    val header = line(11f) + 12 // 右上角刷新按钮自带上下 6dp 内边距，比一行字高
    val reserved = padding * 2 + when (sizeClass) {
        // 标题行 + 8 + 「今日」+ 大金额 + 6 + [柱子] + 6 + 「7 天 xx」
        WidgetSizeClass.SMALL -> header + 8 + line(11f) + 2 + line(30f) + 6 + line(11f) + 6 + 8
        // 标题行 + 12 + 金额块 + 6 + token 行 + 10 + [柱子]
        WidgetSizeClass.MEDIUM -> header + 12 + line(11f) + 2 + line(28f) + 6 + line(11f) + 10 + 8
        // MEDIUM 的固定部分 + 4 + 日标签 + 10 + 「按工具」标题 + 4 + 三行
        WidgetSizeClass.LARGE -> header + 12 + line(11f) + 2 + line(32f) + 6 + line(11f) + 10 +
            4 + line(11f) + 10 + line(11f) + 4 + 3 * line(13f) + 8
    }
    return (cardHeight - reserved).coerceIn(0f, 220f)
}

private fun refreshHint(s: WidgetSnapshot): String {
    if (s.refreshing) return "刷新中…"
    val at = s.updatedAt ?: return "刷新"
    val text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
    return "刷新 · $text"
}
