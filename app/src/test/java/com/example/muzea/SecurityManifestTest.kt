package com.example.muzea

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Статические security-инварианты манифеста: без облачного бэкапа данных,
 * без cleartext, FileProvider не экспортирован, у активности есть явный exported.
 */
class SecurityManifestTest {

    private val manifest: String by lazy {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        )
        candidates.firstOrNull { it.exists() }?.readText()
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")
    }

    @Test
    fun `backup is disabled`() {
        assertTrue(manifest, manifest.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun `cleartext traffic is disabled`() {
        assertTrue(manifest, manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(
            manifest,
            manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\"")
        )
    }

    @Test
    fun `file provider is not exported`() {
        val provider = manifest.substringAfter("<provider").substringBefore(">")
        assertTrue(provider, provider.contains("android:exported=\"false\""))
        assertTrue(provider, provider.contains("android:grantUriPermissions=\"true\""))
    }

    @Test
    fun `news creation activity is not exported`() {
        val activity = manifest.substringAfter(".ui.news.CreateNewsActivity").substringBefore("/>")
        assertTrue(activity, activity.contains("android:exported=\"false\""))
    }
}
