package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun `invalid legacy base url drops key instead of sending it to default host`() {
        val resolved = resolveStoredCredentials(
            keyBlob = "encrypted-blob",
            rawBaseUrl = "http://selfhost.example:8080",
            decrypt = { "vbu_secret" },
        )

        assertNull(resolved.apiKey)
        assertEquals(DEFAULT_BASE_URL, resolved.baseUrl)
        assertTrue(resolved.serverAddressInvalid)
    }

    @Test
    fun `valid stored base url keeps key and strips query and fragment`() {
        val resolved = resolveStoredCredentials(
            keyBlob = "encrypted-blob",
            rawBaseUrl = "https://example.com/path?token=1#frag",
            decrypt = { "vbu_secret" },
        )

        assertEquals("vbu_secret", resolved.apiKey)
        assertEquals("https://example.com/path", resolved.baseUrl)
        assertFalse(resolved.serverAddressInvalid)
    }

    @Test
    fun `missing stored url uses default host and still allows saved key`() {
        val resolved = resolveStoredCredentials(
            keyBlob = "encrypted-blob",
            rawBaseUrl = null,
            decrypt = { "vbu_secret" },
        )

        assertEquals("vbu_secret", resolved.apiKey)
        assertEquals(DEFAULT_BASE_URL, resolved.baseUrl)
        assertFalse(resolved.serverAddressInvalid)
    }

    @Test
    fun `loopback http stays allowed for local debugging`() {
        val resolved = resolveStoredCredentials(
            keyBlob = "encrypted-blob",
            rawBaseUrl = "http://10.0.2.2:8080",
            decrypt = { "vbu_secret" },
        )

        assertEquals("vbu_secret", resolved.apiKey)
        assertEquals("http://10.0.2.2:8080", resolved.baseUrl)
        assertFalse(resolved.serverAddressInvalid)
    }

    @Test
    fun `legacy vibecafe domain migrates to vibefriends while keeping key`() {
        val resolved = resolveStoredCredentials(
            keyBlob = "encrypted-blob",
            rawBaseUrl = "https://vibecafe.ai",
            decrypt = { "vbu_secret" },
        )

        assertEquals("vbu_secret", resolved.apiKey)
        assertEquals(DEFAULT_BASE_URL, resolved.baseUrl)
        assertFalse(resolved.serverAddressInvalid)
    }

    @Test
    fun `insecure legacy vibecafe http stays rejected`() {
        val resolved = resolveStoredCredentials(
            keyBlob = "encrypted-blob",
            rawBaseUrl = "http://vibecafe.ai",
            decrypt = { "vbu_secret" },
        )

        assertNull(resolved.apiKey)
        assertEquals(DEFAULT_BASE_URL, resolved.baseUrl)
        assertTrue(resolved.serverAddressInvalid)
    }
}
