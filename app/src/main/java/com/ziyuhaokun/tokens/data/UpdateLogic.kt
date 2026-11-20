package com.ziyuhaokun.tokens.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** 更新源：只认这个仓库的 Release。 */
internal const val UPDATE_REPO = "ziyuhaokun/tokens"

/** GitHub 上最新一个正式 Release 里的 APK。 */
data class ReleaseInfo(
    /** Release 的 tag，例如 `v0.10.1`。 */
    val tag: String,
    /** 去掉 v 前缀的版本号，例如 `0.10.1`。 */
    val version: String,
    /** Release 说明（原始 Markdown 文本）。 */
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    /** 小写十六进制 SHA-256；GitHub 没给就是 null。 */
    val sha256: String?,
)

/**
 * 自动更新里不依赖 Android 的部分：版本比较、解析 Release、校验下载地址。
 * 单独拎出来是为了能写单元测试——这些判断错了，要么漏更新，要么下到不该下的东西。
 */
object UpdateLogic {

    /** 下载只允许去这些域名（GitHub 自己的发布文件域名）。重定向的每一跳都要过这一关。 */
    private val ALLOWED_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )

    /** APK 体积上限：现在约 3MB，留足余量；超了说明不对劲。 */
    const val MAX_APK_BYTES = 100L * 1024 * 1024

    /**
     * 把 `v0.10.1` / `0.10.1-beta` 解析成数字段 [0, 10, 1]。
     * 只取 `-` 或 `+` 之前的数字部分；不是纯数字段就返回 null。
     */
    fun parseVersion(raw: String): List<Int>? {
        val core = raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        val parts = core.split('.')
        if (parts.size > 4) return null
        return parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    }

    /** [remote] 是否比 [local] 新。任何一边解析不了都当作「没有更新」。 */
    fun isNewer(remote: String, local: String): Boolean {
        val a = parseVersion(remote) ?: return false
        val b = parseVersion(local) ?: return false
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * 解析 `GET /repos/{repo}/releases/latest` 的响应。
     * 草稿 / 预发布、没有 APK 附件、下载地址不是本仓库的 github.com 地址，一律返回 null。
     * 附件按「后缀是 .apk」找，不认固定文件名（以前发过 app-release.apk，也发过 tokens-v0.10.0.apk）。
     */
    fun parseRelease(json: String, repo: String = UPDATE_REPO): ReleaseInfo? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) return null
        val tag = root.optString("tag_name").trim()
        val version = tag.removePrefix("v").removePrefix("V")
        if (parseVersion(tag) == null) return null

        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url")
            if (!isTrustedAssetUrl(url, repo)) continue
            val digest = asset.optString("digest")
                .takeIf { it.startsWith("sha256:", ignoreCase = true) }
                ?.substringAfter(':')?.lowercase()
                ?.takeIf { hex -> hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' } }
            return ReleaseInfo(
                tag = tag,
                version = version,
                notes = root.optString("body").trim(),
                apkName = name,
                apkUrl = url,
                apkSize = asset.optLong("size", -1L),
                sha256 = digest,
            )
        }
        return null
    }

    /** Release 附件的初始地址必须是 `https://github.com/<repo>/releases/download/...`。 */
    fun isTrustedAssetUrl(url: String, repo: String = UPDATE_REPO): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps || parsed.host != "github.com") return false
        if (parsed.port != 443) return false
        return parsed.encodedPath.startsWith("/$repo/releases/download/")
    }

    /** 下载途中（含每一次重定向）允许访问的地址：https + GitHub 的发布文件域名。 */
    fun isAllowedDownloadUrl(url: HttpUrl): Boolean =
        url.isHttps && url.port == 443 && url.host in ALLOWED_DOWNLOAD_HOSTS

    /** 去掉 Markdown 记号，给弹窗里当纯文本显示：`### 标题` → `标题`，`- 项` → `• 项`，去掉反引号。 */
    fun plainNotes(markdown: String): String =
        markdown.lineSequence()
            .map { it.trimEnd() }
            .map { line ->
                when {
                    line.trimStart().startsWith("#") -> line.trimStart().trimStart('#').trim()
                    line.trimStart().startsWith("- ") -> "• " + line.trimStart().removePrefix("- ")
                    else -> line
                }
            }
            .joinToString("\n")
            .replace("`", "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}
