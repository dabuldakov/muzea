package com.example.muzea.ui.chat

import android.content.Context
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Защита от поломок разметки плавающей модалки: раньше ловили крэш при инфляции. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PopupLayoutTest {

    @Test
    fun `popup layout inflates with all sections`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = androidx.appcompat.view.ContextThemeWrapper(base, R.style.Theme_Muzea)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.popup_message_actions, null, false)

        assertNotNull(view.findViewById<android.view.View>(R.id.recentReactions))
        assertNotNull(view.findViewById<android.view.View>(R.id.fullReactions))
        assertNotNull(view.findViewById<android.view.View>(R.id.statusRows))
        assertNotNull(view.findViewById<android.view.View>(R.id.actionsRow))
    }

    @Test
    fun `status section sits under the actions row`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = androidx.appcompat.view.ContextThemeWrapper(base, R.style.Theme_Muzea)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.popup_message_actions, null, false) as android.view.ViewGroup

        val actions = view.findViewById<android.view.View>(R.id.actionsRow)
        val status = view.findViewById<android.view.View>(R.id.statusSection)
        assertEquals(true, view.indexOfChild(actions) < view.indexOfChild(status))
    }
}
