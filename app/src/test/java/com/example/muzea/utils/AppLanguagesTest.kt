package com.example.muzea.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class AppLanguagesTest {

    private val defaultLocale = Locale.getDefault()

    @Before
    fun setUp() {
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.getApplicationLocales() } returns LocaleListCompat.getEmptyLocaleList()
    }

    @After
    fun tearDown() {
        unmockkStatic(AppCompatDelegate::class)
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `list has fourteen unique languages and matches locales_config`() {
        val tags = AppLanguages.ALL.map { it.tag }

        assertEquals(14, tags.size)
        assertEquals(tags.size, tags.distinct().size)
        assertEquals(expectedFromXml(), tags.toSet())
    }

    @Test
    fun `label combines flag and native name`() {
        val english = AppLanguages.ALL.first { it.tag == "en" }

        assertEquals("🇬🇧  English", AppLanguages.label(english))
    }

    @Test
    fun `current prefers the per-app locale`() {
        every { AppCompatDelegate.getApplicationLocales() } returns
            LocaleListCompat.forLanguageTags("kk")

        assertEquals("kk", AppLanguages.current().tag)
    }

    @Test
    fun `current falls back to the system language`() {
        Locale.setDefault(Locale.forLanguageTag("en-US"))

        assertEquals("en", AppLanguages.current().tag)
    }

    @Test
    fun `current matches a region-specific tag by language`() {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))

        assertEquals("pt", AppLanguages.current().tag)
    }

    @Test
    fun `unknown system language falls back to the first entry`() {
        Locale.setDefault(Locale.forLanguageTag("de-DE"))

        assertEquals(AppLanguages.ALL.first().tag, AppLanguages.current().tag)
    }

    @Test
    fun `set delegates to AppCompatDelegate`() {
        AppLanguages.set("es")

        verify { AppCompatDelegate.setApplicationLocales(any()) }
    }

    private fun expectedFromXml(): Set<String> {
        val file = listOf(
            File("src/main/res/xml/locales_config.xml"),
            File("app/src/main/res/xml/locales_config.xml")
        ).first { it.exists() }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("locale")
        val tags = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val attributes = nodes.item(i).attributes
            attributes.getNamedItem("android:name")?.nodeValue?.let(tags::add)
        }
        assertTrue("locales_config.xml is empty", tags.isNotEmpty())
        return tags
    }
}
