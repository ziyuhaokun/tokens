package com.ziyuhaokun.tokens.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    private val goodUrl = "https://github.com/ziyuhaokun/tokens/releases/download/v0.10.1/tokens-v0.10.1.apk"
    private val digest = "56ef52eaf12d5bcb4f5a22290d68a70af0cda54a0b390ad74f698e09ef76daa8"

    private fun release(
        tag: String = "v0.10.1",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """[{"name":"tokens-v0.10.1.apk","size":3005370,"digest":"sha256:$digest","browser_download_url":"$goodUrl"}]""",
    ) = """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"body":"### 标题\n- 一项\n`code`","assets":$assets}"""

    @Test
    fun `parseVersion accepts v prefix and suffixes`() {
        assertEquals(listOf(0, 10, 1), UpdateLogic.parseVersion("v0.10.1"))
        assertEquals(listOf(0, 10, 1), UpdateLogic.parseVersion("0.10.1-beta.2"))
        assertEquals(listOf(1, 0), UpdateLogic.parseVersion("V1.0+build5"))
    }

    @Test
    fun `parseVersion rejects garbage`() {
        assertNull(UpdateLogic.parseVersion(""))
        assertNull(UpdateLogic.parseVersion("latest"))
        assertNull(UpdateLogic.parseVersion("1.x.3"))
        assertNull(UpdateLogic.parseVersion("1.2.3.4.5"))
        assertNull(UpdateLogic.parseVersion("-1.0"))
    }

    @Test
    fun `isNewer compares numerically, not as strings`() {
        assertTrue(UpdateLogic.isNewer("v0.10.0", "0.9.3"))
        assertTrue(UpdateLogic.isNewer("0.10.1", "0.10.0"))
        assertTrue(UpdateLogic.isNewer("1.0", "0.99.99"))
        assertFalse(UpdateLogic.isNewer("0.9.3", "0.10.0"))
        assertFalse(UpdateLogic.isNewer("0.10.0", "0.10.0"))
        // 缺的段按 0 算
        assertFalse(UpdateLogic.isNewer("0.10", "0.10.0"))
        assertTrue(UpdateLogic.isNewer("0.10.0.1", "0.10.0"))
    }

    @Test
    fun `isNewer treats unparsable versions as no update`() {
        assertFalse(UpdateLogic.isNewer("latest", "0.10.0"))
        assertFalse(UpdateLogic.isNewer("0.10.1", ""))
    }

    @Test
    fun `parseRelease reads tag, apk, size and digest`() {
        val info = UpdateLogic.parseRelease(release())!!
        assertEquals("v0.10.1", info.tag)
        assertEquals("0.10.1", info.version)
        assertEquals("tokens-v0.10.1.apk", info.apkName)
        assertEquals(goodUrl, info.apkUrl)
        assertEquals(3005370L, info.apkSize)
        assertEquals(digest, info.sha256)
    }

    @Test
    fun `parseRelease picks the apk whatever it is called`() {
        val assets = """[
          {"name":"notes.txt","browser_download_url":"https://github.com/ziyuhaokun/tokens/releases/download/v1/notes.txt"},
          {"name":"app-release.apk","size":1,"browser_download_url":"https://github.com/ziyuhaokun/tokens/releases/download/v1/app-release.apk"}
        ]"""
        val info = UpdateLogic.parseRelease(release(tag = "v1.0.0", assets = assets))!!
        assertEquals("app-release.apk", info.apkName)
        assertNull(info.sha256)
    }

    @Test
    fun `parseRelease ignores drafts, prereleases and releases without apk`() {
        assertNull(UpdateLogic.parseRelease(release(draft = true)))
        assertNull(UpdateLogic.parseRelease(release(prerelease = true)))
        assertNull(UpdateLogic.parseRelease(release(assets = "[]")))
        assertNull(UpdateLogic.parseRelease(release(tag = "nightly")))
        assertNull(UpdateLogic.parseRelease("not json"))
    }

    @Test
    fun `parseRelease refuses apk urls that are not from this repo on github`() {
        fun withUrl(url: String) =
            release(assets = """[{"name":"a.apk","size":1,"browser_download_url":"$url"}]""")
        assertNull(UpdateLogic.parseRelease(withUrl("https://evil.example/ziyuhaokun/tokens/releases/download/v1/a.apk")))
        assertNull(UpdateLogic.parseRelease(withUrl("http://github.com/ziyuhaokun/tokens/releases/download/v1/a.apk")))
        assertNull(UpdateLogic.parseRelease(withUrl("https://github.com/someone/else/releases/download/v1/a.apk")))
        assertNull(UpdateLogic.parseRelease(withUrl("https://github.com.evil.example/ziyuhaokun/tokens/releases/download/v1/a.apk")))
        assertNull(UpdateLogic.parseRelease(withUrl("https://github.com@evil.example/ziyuhaokun/tokens/releases/download/v1/a.apk")))
    }

    @Test
    fun `malformed digest is dropped, not trusted`() {
        val assets = """[{"name":"a.apk","size":1,"digest":"sha256:zzz","browser_download_url":"$goodUrl"}]"""
        assertNull(UpdateLogic.parseRelease(release(assets = assets))!!.sha256)
        val md5 = """[{"name":"a.apk","size":1,"digest":"md5:$digest","browser_download_url":"$goodUrl"}]"""
        assertNull(UpdateLogic.parseRelease(release(assets = md5))!!.sha256)
    }

    @Test
    fun `download hops are limited to github release hosts over https`() {
        assertTrue(UpdateLogic.isAllowedDownloadUrl("https://github.com/x".toHttpUrl()))
        assertTrue(UpdateLogic.isAllowedDownloadUrl("https://objects.githubusercontent.com/x".toHttpUrl()))
        assertTrue(UpdateLogic.isAllowedDownloadUrl("https://release-assets.githubusercontent.com/x".toHttpUrl()))
        assertFalse(UpdateLogic.isAllowedDownloadUrl("http://github.com/x".toHttpUrl()))
        assertFalse(UpdateLogic.isAllowedDownloadUrl("https://raw.githubusercontent.com/x".toHttpUrl()))
        assertFalse(UpdateLogic.isAllowedDownloadUrl("https://evil.example/x".toHttpUrl()))
        assertFalse(UpdateLogic.isAllowedDownloadUrl("https://github.com.evil.example/x".toHttpUrl()))
        assertFalse(UpdateLogic.isAllowedDownloadUrl("https://github.com:8443/x".toHttpUrl()))
    }

    @Test
    fun `plainNotes strips markdown marks`() {
        val text = UpdateLogic.plainNotes("## Tokens v1\n\n\n\n### 修复\n- 一项 `code`\n  - 二项")
        assertEquals("Tokens v1\n\n修复\n• 一项 code\n• 二项", text)
        assertNotNull(text)
    }
}
