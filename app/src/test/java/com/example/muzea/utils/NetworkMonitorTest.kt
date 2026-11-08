package com.example.muzea.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ветки NetworkMonitor: нет сети, без валидации, weak и good. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NetworkMonitorTest {

    private fun monitor(
        network: Network?,
        capabilities: NetworkCapabilities?
    ): NetworkMonitor {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(any()) } returns capabilities
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns cm
        return NetworkMonitor(context)
    }

    private fun capabilities(
        validated: Boolean,
        metered: Boolean,
        downKbps: Int
    ): NetworkCapabilities {
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns validated
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns !metered
        every { caps.linkDownstreamBandwidthKbps } returns downKbps
        return caps
    }

    @Test
    fun `no active network is offline`() {
        val status = monitor(null, null).status.value

        assertEquals(NetworkQuality.OFFLINE, status.quality)
        assertFalse(status.online)
    }

    @Test
    fun `missing capabilities is offline`() {
        val status = monitor(mockk(relaxed = true), null).status.value

        assertEquals(NetworkQuality.OFFLINE, status.quality)
    }

    @Test
    fun `unvalidated network is offline`() {
        val status = monitor(
            mockk(relaxed = true),
            capabilities(validated = false, metered = false, downKbps = 10_000)
        ).status.value

        assertEquals(NetworkQuality.OFFLINE, status.quality)
    }

    @Test
    fun `slow metered network is weak`() {
        val status = monitor(
            mockk(relaxed = true),
            capabilities(validated = true, metered = true, downKbps = 200)
        ).status.value

        assertEquals(NetworkQuality.WEAK, status.quality)
        assertTrue(status.metered)
        assertTrue(status.online)
    }

    @Test
    fun `fast unmetered network is good`() {
        val status = monitor(
            mockk(relaxed = true),
            capabilities(validated = true, metered = false, downKbps = 20_000)
        ).status.value

        assertEquals(NetworkQuality.GOOD, status.quality)
        assertFalse(status.metered)
    }
}
