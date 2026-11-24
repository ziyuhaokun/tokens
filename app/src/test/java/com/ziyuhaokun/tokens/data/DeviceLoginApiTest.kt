package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLoginApiTest {

    @Test
    fun `rewrites internal bind address 0_0_0_0 to baseUrl origin`() {
        val resolved = DeviceLoginApi.resolveVerificationUri(
            rawComplete = "https://0.0.0.0:3000/usage/device?code=HT9T-69V2",
            rawUri = "https://0.0.0.0:3000/usage/device",
            userCode = "HT9T-69V2",
            baseUrl = "https://vibefriends.com",
        )

        assertEquals("https://vibefriends.com/usage/device?code=HT9T-69V2", resolved)
    }

    @Test
    fun `rewrites fallback verificationUri when complete url is blank`() {
        val resolved = DeviceLoginApi.resolveVerificationUri(
            rawComplete = "",
            rawUri = "http://localhost:3000/usage/device",
            userCode = "AB CD&x=1",
            baseUrl = "https://vibefriends.com",
        )

        assertEquals("https://vibefriends.com/usage/device?code=AB+CD%26x%3D1", resolved)
    }

    @Test
    fun `keeps third-party host untouched so UI security check still blocks it`() {
        val resolved = DeviceLoginApi.resolveVerificationUri(
            rawComplete = "https://evil.example/usage/device?code=1234",
            rawUri = "https://evil.example/usage/device",
            userCode = "1234",
            baseUrl = "https://vibefriends.com",
        )

        assertEquals("https://evil.example/usage/device?code=1234", resolved)
    }

    @Test
    fun `keeps suspicious backslash or at-sign urls untouched`() {
        val raw = "https://0.0.0.0:3000\\@evil.example/usage/device?code=1234"
        val resolved = DeviceLoginApi.resolveVerificationUri(
            rawComplete = raw,
            rawUri = "",
            userCode = "1234",
            baseUrl = "https://vibefriends.com",
        )

        assertEquals(raw, resolved)
    }
}
