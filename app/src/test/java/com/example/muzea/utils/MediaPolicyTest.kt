package com.example.muzea.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPolicyTest {

    @Test
    fun `good network autoloads media`() {
        MediaPolicy.status = NetworkStatus(NetworkQuality.GOOD, metered = false)
        assertTrue(MediaPolicy.autoLoad)
        assertFalse(MediaPolicy.cacheOnly)
    }

    @Test
    fun `weak network does not autoload media`() {
        MediaPolicy.status = NetworkStatus(NetworkQuality.WEAK, metered = true)
        assertFalse(MediaPolicy.autoLoad)
        assertFalse(MediaPolicy.cacheOnly)
    }

    @Test
    fun `offline uses disk cache only`() {
        MediaPolicy.status = NetworkStatus(NetworkQuality.OFFLINE, metered = false)
        assertFalse(MediaPolicy.autoLoad)
        assertTrue(MediaPolicy.cacheOnly)
    }
}
