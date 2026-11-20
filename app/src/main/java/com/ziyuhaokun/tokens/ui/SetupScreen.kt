package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.DEFAULT_BASE_URL

@Composable
fun SetupScreen(
    saving: Boolean,
    error: String?,
    onSave: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(64.dp))
        Text(
            text = "Tokens",
            color = Palette.Text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "输入 vibecafe.ai 的 API Key，直接读云端用量。",
            color = Palette.Muted,
            fontSize = TokensType.Body,
        )

        Spacer(Modifier.height(28.dp))
        SoftCard(contentPadding = TokensSpace.CardPaddingCompact) {
            CredentialsForm(
                initialKey = "",
                initialUrl = DEFAULT_BASE_URL,
                saving = saving,
                error = error,
                saveLabel = "验证并保存",
                onSave = onSave,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = buildAnnotatedString {
                append("Key 从哪来：在电脑上跑过 vibe-usage（CLI 或桌面端）后，")
                withStyle(SpanStyle(fontFamily = NumericFont)) { append("~/.vibe-usage/config.json") }
                append(" 里的 apiKey；或在 vibecafe.ai/usage/setup 预先签一个 ")
                withStyle(SpanStyle(fontFamily = NumericFont)) { append("vbu_") }
                append(" 开头的 key。")
            },
            color = Palette.Dim,
            fontSize = TokensType.Caption,
            lineHeight = TokensType.Caption * 1.5f,
        )
    }
}

@Composable
fun CredentialsForm(
    initialKey: String,
    initialUrl: String,
    saving: Boolean,
    error: String?,
    saveLabel: String,
    onSave: (String, String) -> Unit,
    /** true：key 可以留空，表示沿用已保存的 key（设置页只改服务器地址的场景）。 */
    allowBlankKey: Boolean = false,
    onCancel: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
) {
    var key by remember { mutableStateOf(initialKey) }
    var url by remember { mutableStateOf(initialUrl) }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim() },
            label = { Text("API Key", fontSize = TokensType.Caption) },
            placeholder = {
                Text(
                    text = if (allowBlankKey) "留空 = 沿用当前 key" else "vbu_…",
                    color = Palette.Dim,
                    fontSize = TokensType.Body,
                )
            },
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            Text(
                text = if (showAdvanced) "收起高级设置" else "高级设置（服务端地址）",
                color = Palette.Dim,
                fontSize = TokensType.Label,
            )
        }
        if (showAdvanced) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.trim() },
                label = { Text("API 地址", fontSize = TokensType.Caption) },
                singleLine = true,
                enabled = !saving,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(text = error, color = Palette.Warn, fontSize = TokensType.Caption)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(key, url.ifBlank { DEFAULT_BASE_URL }) },
            enabled = !saving && (allowBlankKey || key.isNotBlank()),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.Text,
                contentColor = Palette.Bg,
                disabledContainerColor = Palette.Hairline,
                disabledContentColor = Palette.Dim,
            ),
        ) {
            Text(
                text = if (saving) "验证中…" else saveLabel,
                fontSize = TokensType.Body,
                fontWeight = FontWeight.Medium,
            )
        }

        if (onCancel != null || onSignOut != null) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (onSignOut != null) {
                    TextButton(onClick = onSignOut) {
                        Text("退出登录", color = Palette.Warn, fontSize = TokensType.Body)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onCancel != null) {
                    TextButton(onClick = onCancel) {
                        Text("取消", color = Palette.Muted, fontSize = TokensType.Body)
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Muted,
    unfocusedBorderColor = Palette.Hairline,
    focusedTextColor = Palette.Text,
    unfocusedTextColor = Palette.Text,
    focusedLabelColor = Palette.Muted,
    unfocusedLabelColor = Palette.Dim,
    cursorColor = Palette.Text,
)
