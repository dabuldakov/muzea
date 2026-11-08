package com.example.muzea

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Инварианты release-сборки: R8 включён, а классы, которые Gson/Retrofit
 * читают рефлексией, защищены keep-правилами. Без этого в 1.7.19 и
 * 1.7.28-beta падало на старте/после отправки фото.
 */
class ReleaseShrinkRulesTest {

    private val rules: String by lazy {
        listOf(
            File("proguard-rules.pro"),
            File("app/proguard-rules.pro"),
            File("../app/proguard-rules.pro")
        ).firstOrNull { it.exists() }?.readText()
            ?: error("proguard-rules.pro not found from ${File(".").absolutePath}")
    }

    private val gradle: String by lazy {
        listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts"),
            File("../app/build.gradle.kts")
        ).firstOrNull { it.exists() }?.readText()
            ?: error("app/build.gradle.kts not found from ${File(".").absolutePath}")
    }

    @Test
    fun `release keeps minification enabled`() {
        assertTrue(gradle, gradle.contains("isMinifyEnabled = true"))
    }

    @Test
    fun `outbox queue is kept from obfuscation`() {
        assertTrue(
            rules,
            rules.contains("-keep class com.example.muzea.data.repository.OutboxItem { *; }")
        )
        assertTrue(
            rules,
            rules.contains("-keep class com.example.muzea.data.repository.OutboxStore { *; }")
        )
    }

    @Test
    fun `gson reflection signatures are kept`() {
        assertTrue(rules, rules.contains("-keepattributes Signature"))
        assertTrue(rules, rules.contains("-keep class com.example.muzea.domain.model.** { *; }"))
        assertTrue(rules, rules.contains("-keep class com.example.muzea.data.model.** { *; }"))
    }

    @Test
    fun `retrofit api and github models are kept`() {
        assertTrue(rules, rules.contains("interface com.example.muzea.data.api.**"))
        assertTrue(rules, rules.contains("com.example.muzea.data.api.GitHubRelease"))
        assertTrue(rules, rules.contains("com.example.muzea.data.api.GitHubAsset"))
    }
}
