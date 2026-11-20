package com.ziyuhaokun.tokens.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.sqrt

/** 从触点算到屏幕最远角的距离：圆扩到这个半径就盖满整屏。 */
fun farthestCornerDistance(center: Offset, size: IntSize): Float {
    if (size == IntSize.Zero) return 0f
    val dx = maxOf(center.x, size.width - center.x)
    val dy = maxOf(center.y, size.height - center.y)
    return sqrt(dx * dx + dy * dy)
}
