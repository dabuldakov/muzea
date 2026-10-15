package com.example.muzea.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateVersionTest {

    @Test
    fun `newer patch version is detected`() {
        assertTrue(isNewerVersion("1.2.1", "1.2.0"))
    }

    @Test
    fun `newer minor and major versions are detected`() {
        assertTrue(isNewerVersion("1.3.0", "1.2.9"))
        assertTrue(isNewerVersion("2.0.0", "1.99.99"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(isNewerVersion("1.2.0", "1.2.0"))
    }

    @Test
    fun `older version is not newer`() {
        assertFalse(isNewerVersion("1.2.0", "1.2.1"))
        assertFalse(isNewerVersion("1.2.0", "2.0.0"))
    }

    @Test
    fun `missing parts are treated as zero`() {
        assertFalse(isNewerVersion("1.2", "1.2.0"))
        assertTrue(isNewerVersion("1.2.1", "1.2"))
    }

    @Test
    fun `non numeric suffix is treated as zero`() {
        assertFalse(isNewerVersion("1.2.0-beta", "1.2.0"))
    }
}
