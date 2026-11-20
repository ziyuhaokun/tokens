package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UsageCachePruneTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, bytes: Int, modified: Long): File =
        File(dir, name).apply {
            writeBytes(ByteArray(bytes))
            setLastModified(modified)
        }

    @Test
    fun `other accounts and leftover temp files are removed, current account stays`() {
        val dir = tmp.newFolder()
        val mine = file(dir, "aaaa-today.json", 10, 1_000)
        val mineYear = file(dir, "aaaa-year.json", 10, 2_000)
        val other = file(dir, "bbbb-today.json", 10, 3_000)
        val otherTmp = file(dir, "bbbb-year.json.tmp", 10, 4_000)

        val removed = pruneCacheDir(dir, "aaaa", maxBytes = 1_000)

        assertEquals(2, removed)
        assertTrue(mine.exists())
        assertTrue(mineYear.exists())
        assertFalse(other.exists())
        assertFalse(otherTmp.exists())
    }

    @Test
    fun `over the size cap the oldest files go first`() {
        val dir = tmp.newFolder()
        val oldest = file(dir, "aaaa-a.json", 100, 1_000)
        val middle = file(dir, "aaaa-b.json", 100, 2_000)
        val newest = file(dir, "aaaa-c.json", 100, 3_000)

        val removed = pruneCacheDir(dir, "aaaa", maxBytes = 200)

        assertEquals(1, removed)
        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
    }

    @Test
    fun `scope prefix must match the whole scope plus dash`() {
        val dir = tmp.newFolder()
        // scope "aaaa" 不能误留 "aaaab-…"（另一个账号的作用域恰好以 aaaa 开头）
        val lookalike = file(dir, "aaaab-today.json", 10, 1_000)
        val mine = file(dir, "aaaa-today.json", 10, 2_000)

        pruneCacheDir(dir, "aaaa", maxBytes = 1_000)

        assertFalse(lookalike.exists())
        assertTrue(mine.exists())
    }

    @Test
    fun `missing directory is a no-op`() {
        assertEquals(0, pruneCacheDir(File(tmp.root, "nope"), "aaaa", 100))
    }
}
