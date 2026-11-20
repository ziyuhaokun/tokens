package com.ziyuhaokun.tokens.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ziyuhaokun.tokens.R

/**
 * 小票用的等宽字体：打包进来的 JetBrains Mono（SIL OFL 1.1，见 third_party/JetBrainsMono-OFL.txt）。
 * 三个字重覆盖正文/强调/标题；Light 落到 Regular，避免细字重在小票上糊掉。
 */
val ReceiptFont: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Light),
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
