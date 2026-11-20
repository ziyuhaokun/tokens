package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
            .padding(20.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Tokens", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "输入 vibecafe.ai 的 API Key，直接读云端的用量数据。",
            color = Palette.Muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(24.dp))
        CredentialsForm(
            initialKey = "",
            initialUrl = DEFAULT_BASE_URL,
            saving = saving,
            error = error,
            saveLabel = "验证并保存",
            onSave = onSave,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Key 从哪来：在电脑上跑过 vibe-usage（CLI 或桌面端）后，~/.vibe-usage/config.json 里的 apiKey；" +
                "或在 vibecafe.ai/usage/setup 预先签一个 vbu_ 开头的 key。",
            color = Palette.Dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
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
            label = { Text("API Key", fontSize = 12.sp) },
            placeholder = { Text("vbu_…", color = Palette.Dim, fontSize = 13.sp) },
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { showAdvanced = !showAdvanced }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text(
                text = if (showAdvanced) "收起高级设置" else "高级设置（服务端地址）",
                color = Palette.Dim,
                fontSize = 11.sp,
            )
        }
        if (showAdvanced) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.trim() },
                label = { Text("API 地址", fontSize = 12.sp) },
                singleLine = true,
                enabled = !saving,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Palette.Surface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Palette.Danger.copy(alpha = 0.4f)),
            ) {
                Text(
                    text = error,
                    color = Palette.Danger,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(key, url.ifBlank { DEFAULT_BASE_URL }) },
            enabled = !saving && key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.Bg),
        ) {
            Text(text = if (saving) "验证中…" else saveLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        if (onCancel != null || onSignOut != null) {
            Spacer(Modifier.height(6.dp))
            RowActions(onCancel = onCancel, onSignOut = onSignOut)
        }
    }
}

@Composable
private fun RowActions(onCancel: (() -> Unit)?, onSignOut: (() -> Unit)?) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
        if (onSignOut != null) {
            TextButton(onClick = onSignOut) {
                Text("退出登录", color = Palette.Danger, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        if (onCancel != null) {
            TextButton(onClick = onCancel) {
                Text("取消", color = Palette.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Accent,
    unfocusedBorderColor = Palette.Line,
    focusedTextColor = Palette.Text,
    unfocusedTextColor = Palette.Text,
    focusedLabelColor = Palette.Accent,
    unfocusedLabelColor = Palette.Muted,
    cursorColor = Palette.Accent,
)
