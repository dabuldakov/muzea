package com.example.muzea

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Правовые ссылки и текст удаления должны следовать языку приложения. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LegalLinksTest {

    private fun resources(locale: Locale): android.content.res.Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration).resources
    }

    @Test
    fun `english opens english legal pages`() {
        val resources = resources(Locale.ENGLISH)

        assertEquals(
            "https://muzea.su/en/privacy?lang=en",
            resources.getString(R.string.policy_url)
        )
        assertEquals(
            "https://muzea.su/en/terms?lang=en",
            resources.getString(R.string.terms_url)
        )
        assertTrue(resources.getString(R.string.delete_account_message).contains("30 days"))
    }

    @Test
    fun `russian opens russian legal pages`() {
        val resources = resources(Locale.forLanguageTag("ru"))

        assertEquals(
            "https://muzea.su/privacy?lang=ru",
            resources.getString(R.string.policy_url)
        )
        assertEquals(
            "https://muzea.su/terms?lang=ru",
            resources.getString(R.string.terms_url)
        )
        assertTrue(resources.getString(R.string.delete_account_message).contains("30 дней"))
    }

    @Test
    fun `other locales fall back to english legal pages`() {
        val resources = resources(Locale.forLanguageTag("de"))

        assertEquals(
            "https://muzea.su/en/privacy?lang=en",
            resources.getString(R.string.policy_url)
        )
    }
}
