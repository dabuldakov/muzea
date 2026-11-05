package com.example.muzea.data.repository

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Превью ссылок не должно ходить на localhost/приватные адреса (SSRF) и не
 * должно принимать не-HTTP схемы.
 */
class LinkPreviewStoreSecurityTest {

    private val store = LinkPreviewStore()

    @Test
    fun `blocks loopback, private and metadata hosts`() {
        listOf(
            "127.0.0.1",
            "10.0.0.1",
            "172.16.0.1",
            "192.168.1.1",
            "169.254.169.254",
            "0.0.0.0",
            "::1",
            "localhost",
            "api.localhost",
            "printer.local",
            "metadata.google.internal",
            "intranet"
        ).forEach { host ->
            assertTrue("должен блокироваться: $host", store.isBlockedHost(host))
        }
    }

    @Test
    fun `allows public hosts`() {
        listOf("example.com", "8.8.8.8", "api.klipy.com").forEach { host ->
            assertFalse("не должен блокироваться: $host", store.isBlockedHost(host))
        }
    }

    @Test
    fun `safe url requires http scheme and public host`() {
        assertTrue(store.isSafeUrl("https://example.com/a".toHttpUrl()))
        assertFalse(store.isSafeUrl("http://127.0.0.1/a".toHttpUrl()))
        assertFalse(store.isSafeUrl("http://localhost/a".toHttpUrl()))
    }

    @Test
    fun `load rejects unsafe urls without touching the network`() = runTest {
        assertNull(store.load("file:///etc/passwd"))
        assertNull(store.load("ftp://example.com/x"))
        assertNull(store.load("http://127.0.0.1/secret"))
        assertNull(store.load("http://169.254.169.254/latest/meta-data"))
        assertNull(store.load("http://localhost:8080/admin"))
    }
}
