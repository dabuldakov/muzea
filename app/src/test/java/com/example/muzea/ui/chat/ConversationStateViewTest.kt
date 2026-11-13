package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.utils.NetworkQuality
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ConversationStateViewTest {

    private val context: Context = androidx.appcompat.view.ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Muzea
    )

    @Test
    fun `loading without messages shows the spinner`() {
        val progress = View(context)
        val error = TextView(context)
        val list = View(context)
        ConversationStateView(progress, error, list)
            .render(isLoading = true, hasMessages = false, errorText = null)

        assertEquals(View.VISIBLE, progress.visibility)
        assertEquals(View.GONE, list.visibility)
        assertEquals(View.GONE, error.visibility)
    }

    @Test
    fun `error without messages hides the list and shows the text`() {
        val error = TextView(context)
        val list = View(context)
        ConversationStateView(View(context), error, list)
            .render(isLoading = false, hasMessages = false, errorText = "нет сети")

        assertEquals(View.VISIBLE, error.visibility)
        assertEquals("нет сети", error.text.toString())
        assertEquals(View.GONE, list.visibility)
    }

    @Test
    fun `messages show the list and hide the error`() {
        val error = TextView(context)
        val list = View(context)
        ConversationStateView(View(context), error, list)
            .render(isLoading = true, hasMessages = true, errorText = "старая ошибка")

        assertEquals(View.VISIBLE, list.visibility)
        assertEquals(View.GONE, error.visibility)
    }

    @Test
    fun `network banner shows offline and slow states`() {
        val banner = TextView(context)
        var online = 0
        val view = NetworkBannerView(banner) { online++ }

        view.render(NetworkQuality.OFFLINE)
        assertEquals(View.VISIBLE, banner.visibility)
        assertEquals(context.getString(R.string.network_offline_banner), banner.text.toString())

        view.render(NetworkQuality.WEAK)
        assertEquals(context.getString(R.string.network_slow_banner), banner.text.toString())

        view.render(NetworkQuality.GOOD)
        assertEquals(View.GONE, banner.visibility)
        assertEquals(1, online)
    }
}
