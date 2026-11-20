// visualTransformation 在 Compose 1.10 被标记废弃，官方指向 BasicSecureTextField；
// 那个的样式和这一整套输入框对不上，先用旧 API 并在本文件屏蔽废弃告警。
@file:Suppress("DEPRECATION")

package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.DEFAULT_BASE_URL
import com.ziyuhaokun.tokens.data.normalizeBaseUrl

@Composable
fun SetupScreen(
    saving: Boolean,
    error: String?,
    login: LoginPhase,
    onLogin: () -> Unit,
    onCancelLogin: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var showManual by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 拿到验证码就把浏览器打开；用户手动关掉后还能再点「重新打开」。
    var openedCode by remember { mutableStateOf<String?>(null) }
    val waiting = login as? LoginPhase.Waiting
    LaunchedEffect(waiting?.userCode) {
        val target = waiting ?: return@LaunchedEffect
        if (openedCode == target.userCode) return@LaunchedEffect
        openedCode = target.userCode
        // 链接是服务端给的，先确认是自己那个域名的 https 页面再打开。
        val safe = trustedVerificationUri(target.verificationUri, target.baseUrl) ?: return@LaunchedEffect
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, safe)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp),
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
            text = "登录 vibecafe.ai，直接读云端用量。",
            color = Palette.Muted,
            fontSize = TokensType.Body,
        )

        Spacer(Modifier.height(28.dp))
        SoftCard(contentPadding = TokensSpace.CardPaddingCompact) {
            when {
                waiting != null -> LoginWaiting(waiting, onCancelLogin)
                else -> {
                    LoginMessages(login)
                    Button(
                        onClick = onLogin,
                        enabled = login !is LoginPhase.Starting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Palette.Text,
                            contentColor = Palette.Bg,
                            disabledContainerColor = Palette.Hairline,
                            disabledContentColor = Palette.Dim,
                        ),
                    ) {
                        Text(
                            text = if (login is LoginPhase.Starting) "正在获取验证码…" else "登录 vibecafe.ai",
                            fontSize = TokensType.Body,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "会打开浏览器，确认后自动回到 App，不用手动抄 Key。",
                        color = Palette.Dim,
                        fontSize = TokensType.Caption,
                        lineHeight = TokensType.Caption * 1.45f,
                    )
                    Spacer(Modifier.height(10.dp))
                    // 次要操作用描边按钮，和上面的实心主按钮形成「一主一次」，
                    // 不再是贴在卡片边缘的一行小字。
                    OutlinedButton(
                        onClick = { showManual = !showManual },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Palette.Hairline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Palette.TextSoft,
                        ),
                    ) {
                        Text(
                            text = if (showManual) "收起手动输入" else "手动输入 key",
                            fontSize = TokensType.Label,
                        )
                    }
                    if (showManual) {
                        Spacer(Modifier.height(8.dp))
                        CredentialsForm(
                            initialKey = "",
                            initialUrl = DEFAULT_BASE_URL,
                            saving = saving,
                            error = error,
                            saveLabel = "验证并保存",
                            onSave = onSave,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = buildAnnotatedString {
                append("登录后可随时在 vibecafe.ai 撤销这个 App 的访问。也可以用 vibe-usage CLI 生成的 ")
                withStyle(SpanStyle(fontFamily = NumericFont)) { append("vbu_") }
                append(" Key 手动登录。")
            },
            color = Palette.Dim,
            fontSize = TokensType.Caption,
            lineHeight = TokensType.Caption * 1.5f,
        )
    }
}

/** 已经拿到验证码，等用户在浏览器里确认。 */
@Composable
private fun LoginWaiting(waiting: LoginPhase.Waiting, onCancel: () -> Unit) {
    val context = LocalContext.current
    val safeUri = remember(waiting.verificationUri, waiting.baseUrl) {
        trustedVerificationUri(waiting.verificationUri, waiting.baseUrl)
    }
    CardLabel("在浏览器中确认")
    Spacer(Modifier.height(10.dp))
    Text(
        text = waiting.userCode,
        color = Palette.Accent,
        fontSize = 34.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 6.sp,
        fontFamily = NumericFont,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "浏览器里应显示同一个验证码，点「确认链接」即可。没打开就用下面的链接。",
        color = Palette.Dim,
        fontSize = TokensType.Caption,
        lineHeight = TokensType.Caption * 1.45f,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                val target = safeUri ?: return@Button
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
            },
            enabled = safeUri != null,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Text, contentColor = Palette.Bg),
        ) {
            Text("重新打开浏览器", fontSize = TokensType.Label, fontWeight = FontWeight.Medium)
        }
        Button(
            onClick = onCancel,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Field, contentColor = Palette.Text),
        ) {
            Text("取消", fontSize = TokensType.Label)
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = if (safeUri == null) {
            "服务器返回的确认链接不可信（不是同一域名的 https 页面），已拦下。请检查「API 地址」后重新登录。"
        } else {
            "确认后会自动完成，页面会自己跳到用量。"
        },
        color = if (safeUri == null) Palette.Warn else Palette.Dim,
        fontSize = TokensType.Caption,
    )
}

/**
 * 只接受「https（本机调试可用 http）+ 和服务器地址同一个域名」的链接。
 * 服务端响应是不受信输入：放任它就能拉起 intent:// 、sms: 或钓鱼页面，
 * 而用户会在这个页面里输入验证码。
 */
private fun trustedVerificationUri(raw: String, baseUrl: String): Uri? {
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "https" && scheme != "http") return null
    val host = uri.host?.lowercase() ?: return null
    val baseHost = runCatching { java.net.URI(baseUrl).host?.lowercase() }.getOrNull()
    val loopback = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
    if (scheme == "http" && !loopback) return null
    if (!loopback && baseHost != null && host != baseHost) return null
    return uri
}

/** 被拒绝 / 过期 / 出错的提示，颜色跟着严重程度走。 */
@Composable
private fun LoginMessages(login: LoginPhase) {
    val message = when (login) {
        LoginPhase.Denied -> "登录被拒绝，可以重新发起一次。"
        LoginPhase.Expired -> "验证码已过期，请重新登录。"
        is LoginPhase.Failed -> "登录失败：${login.message}"
        else -> null
    }
    if (message != null) {
        Text(text = message, color = Palette.Warn, fontSize = TokensType.Caption)
        Spacer(Modifier.height(10.dp))
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
    // 默认打码，要看再点「显示」：这个框里就是账号的读写凭据。
    var revealKey by remember { mutableStateOf(false) }
    val normalizedUrl = remember(url) { normalizeBaseUrl(url) }
    val urlProblem = if (url.isBlank() || normalizedUrl != null) null else "地址必须以 https:// 开头（本机调试可用 http://localhost）"

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
            visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                TextButton(
                    onClick = { revealKey = !revealKey },
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(
                        text = if (revealKey) "隐藏" else "显示",
                        color = Palette.Dim,
                        fontSize = TokensType.Caption,
                    )
                }
            },
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
                isError = urlProblem != null,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (urlProblem != null) {
                Text(
                    text = urlProblem,
                    color = Palette.Warn,
                    fontSize = TokensType.Caption,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = "API Key 会发到这个地址，只填写你信任的服务器。",
                color = Palette.Dim,
                fontSize = TokensType.Caption,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(text = error, color = Palette.Warn, fontSize = TokensType.Caption)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(key, normalizedUrl ?: DEFAULT_BASE_URL) },
            enabled = !saving && (allowBlankKey || key.isNotBlank()) && normalizedUrl != null,
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
