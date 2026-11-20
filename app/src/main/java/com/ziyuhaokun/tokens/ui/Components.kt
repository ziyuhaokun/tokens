package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.UsageAgg

/**
 * 页面外壳：下拉刷新 + 竖向滚动 + 安全区内边距 + 页面左右留白。
 * 概览和热力两个页面共用，手势和边距保持一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        // 只让顶部和左右避开系统栏；底部让内容穿到手势条下面，才叫沉浸式。
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Palette.CardTop,
                color = Palette.Text,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TokensSpace.Page)
                // 末尾留出手势条的高度，滑到底时最后一张卡片不会被小白条盖住。
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp),
            content = content,
        )
    }
}

/** 桌面端那种卡片：圆角 24、上亮下暗的轻微渐变、1dp 发丝描边。 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = TokensSpace.CardPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(TokensSpace.CardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Palette.CardTop, Palette.CardBottom)))
            .border(1.dp, Palette.HairlineSoft, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Palette.Text,
        fontSize = TokensType.CardTitle,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
fun SmallLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.Dim) {
    Text(text = text, color = color, fontSize = TokensType.Caption, modifier = modifier)
}

/**
 * 「$」小号灰 +「41」大号细体 +「.20」小号灰 —— 桌面端金额排版。
 * integer 以货币符号（$ / ¥）开头时自动把符号拆出来单独排；tokens 这类没有符号的照常显示。
 *
 * 三段拼成**一段文字**（不同 span 不同字号）：天然按基线对齐，而且可以整体自动缩小——
 * 金额很长时（例如 ¥274,240.24）一行放不下就逐级缩到 [minSize]，绝不换行、不裁掉小数。
 */
@Composable
fun BigMoney(
    integer: String,
    decimals: String,
    modifier: Modifier = Modifier,
    size: TextUnit = TokensType.Display,
    minSize: TextUnit = 22.sp,
) {
    // 开头的非数字部分就是货币符号（负号留在数字里）。
    val prefix = integer.takeWhile { !it.isDigit() && it != '-' }
    val digits = integer.drop(prefix.length)
    // 强调色只作用在这类关键数字上；选「单色」时它等于正文色。
    val accent = Palette.Accent
    val muted = Palette.Muted
    val text = remember(prefix, digits, decimals, accent, muted) {
        buildAnnotatedString {
            if (prefix.isNotEmpty()) {
                withStyle(SpanStyle(color = muted, fontSize = 0.52.em)) { append(prefix) }
                // 符号和数字之间留一道细缝（跟着字号缩放）。
                withStyle(SpanStyle(fontSize = 0.12.em)) { append(' ') }
            }
            // 大字收紧一点：字间距跟着字号走。
            withStyle(SpanStyle(color = accent, letterSpacing = (-0.033).em)) { append(digits) }
            withStyle(SpanStyle(color = muted, fontSize = 0.52.em)) { append(decimals) }
        }
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = accent, fontSize = size, fontWeight = FontWeight.Light),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = size, stepSize = 1.sp),
    )
}

/**
 * 单行、放不下就自动缩小的大号文字（例如「2459h 46m」）。
 * 不换行、不截断，最小缩到 [minSize]。
 */
@Composable
fun FitText(
    text: String,
    color: Color,
    size: TextUnit,
    modifier: Modifier = Modifier,
    minSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Light,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = fontWeight, letterSpacing = letterSpacing),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = size, stepSize = 1.sp),
    )
}

/** 「Token  65.5M」这种 label / value 一行的小统计，值用等宽数字。 */
@Composable
fun StatPair(label: String, value: String, valueColor: Color = Palette.Text) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Palette.Dim,
            fontSize = TokensType.Label,
            maxLines = 1,
            softWrap = false,
            // 最小宽度让两行的值对齐；标签比它长（如「Sessions」）时照样留出间距，不会贴住值。
            modifier = if (isLargeText()) Modifier else Modifier.widthIn(min = 48.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = TokensType.Value,
            fontFamily = NumericFont,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 可点的文字链接（「设置」「刷新」「← 返回」）。
 * 视觉上只是一段字，但点击区域至少 44dp 高，手指好按。
 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Palette.Muted,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .heightIn(min = TokensSpace.MinTouch)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = if (enabled) color else Palette.Dim, fontSize = TokensType.Body)
    }
}

/** 一级视图。目前两个，以后加「明细」直接往这里加。 */
enum class AppTab(val label: String) {
    OVERVIEW("概览"),
    YEAR("热力"),
}

/**
 * 标题行：左边 Tokens + 账号，右边一级视图切换 + 设置。
 * 两个页面共用，切页时标题不会跳。
 */
@Composable
fun ScreenHeader(
    subtitle: String,
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Tokens", color = Palette.Text, fontSize = TokensType.Header, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                color = Palette.Dim,
                fontSize = TokensType.Caption,
                fontFamily = NumericFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppTabSwitch(selected = tab, onSelect = onTab)
        Spacer(Modifier.width(4.dp))
        TextAction(text = "设置", onClick = onSettings)
    }
}

@Composable
private fun AppTabSwitch(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Field)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AppTab.entries.forEach { entry ->
            val isOn = entry == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isOn) Palette.CardTop else Color.Transparent)
                    .then(if (isOn) Modifier.border(1.dp, Palette.Hairline, RoundedCornerShape(9.dp)) else Modifier)
                    .semantics { this.selected = isOn }
                    .clickable(role = Role.Tab) { if (!isOn) onSelect(entry) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.label,
                    color = if (isOn) Palette.Text else Palette.Dim,
                    fontSize = TokensType.Label,
                    fontWeight = if (isOn) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 时间范围：纯文字，选中项亮色 + 下方短横线，未选中灰色。每项点击区域 44dp 高。
 *
 * 七档在标准字号下等分一行；大字号下一行放不下，改成可横向滑动。
 */
@Composable
fun RangeTabs(selected: Range, onSelect: (Range) -> Unit, modifier: Modifier = Modifier) {
    if (isLargeText()) {
        Row(
            modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Range.entries.forEach { range ->
                RangeTab(range, range == selected, onSelect, Modifier.padding(horizontal = 10.dp))
            }
        }
    } else {
        Row(modifier = modifier.fillMaxWidth()) {
            Range.entries.forEach { range ->
                RangeTab(range, range == selected, onSelect, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RangeTab(range: Range, active: Boolean, onSelect: (Range) -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = TokensSpace.MinTouch)
            .clip(RoundedCornerShape(10.dp))
            .semantics { this.selected = active }
            .clickable(role = Role.Tab) { onSelect(range) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = range.label,
                color = if (active) Palette.Text else Palette.Dim,
                fontSize = TokensType.Body,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (active) Palette.Accent else Color.Transparent),
            )
        }
    }
}

/**
 * 排行行：名字 + 右列金额 / tokens；底边那条发丝线按费用占比填充，
 * 一眼能看出谁大谁小。没有彩色（填充色跟随强调色，单色时就是灰白）。
 */
@Composable
fun RankingRow(
    row: UsageAgg.Row,
    maxCost: Double,
    showSessions: Boolean = false,
    divider: Boolean = true,
) {
    val nf = LocalNumberFormats.current
    val share = if (maxCost > 0.0) (row.cost / maxCost).toFloat().coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${row.name}，${nf.money(row.cost)}，${nf.tokens(row.tokens)} tokens"
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.name,
                color = Palette.TextSoft,
                fontSize = TokensType.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showSessions) {
                Text(
                    text = "${row.sessions} · ${Fmt.duration(row.activeSeconds)}",
                    color = Palette.Dim,
                    fontSize = TokensType.Caption,
                    fontFamily = NumericFont,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = nf.money(row.cost),
                color = Palette.Text,
                fontSize = TokensType.Value,
                fontFamily = NumericFont,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = nf.tokens(row.tokens),
                color = Palette.Dim,
                fontSize = TokensType.Caption,
                fontFamily = NumericFont,
                maxLines = 1,
                modifier = Modifier.width(60.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (divider) Palette.HairlineSoft else Color.Transparent),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share)
                    .height(2.dp)
                    .background(Palette.Accent.copy(alpha = 0.55f)),
            )
        }
    }
}

@Composable
fun HairlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.HairlineSoft),
    )
}
