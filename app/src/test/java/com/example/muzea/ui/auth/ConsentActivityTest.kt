package com.example.muzea.ui.auth

import android.content.Intent
import android.widget.TextView
import com.example.muzea.R
import com.example.muzea.utils.ConsentManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric-тесты экрана согласия на обработку персональных данных:
 * текст с контактом оператора, принятие/отклонение и ссылки на документы.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ConsentActivityTest {

    private fun launchConsent() =
        Robolectric.buildActivity(ConsentActivity::class.java).setup().get()

    @Test
    fun `body shows the operator contact`() {
        val activity = launchConsent()
        val body = activity.findViewById<TextView>(R.id.tvConsentBody)

        assertEquals(
            activity.getString(R.string.consent_body, activity.getString(R.string.operator_email)),
            body.text.toString()
        )
    }

    @Test
    fun `accept records consent and opens login`() {
        val activity = launchConsent()

        activity.findViewById<android.view.View>(R.id.btnAccept).performClick()

        assertTrue(ConsentManager.isAccepted(activity))
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(LoginActivity::class.java.name, started?.component?.className)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `decline finishes without recording consent`() {
        val activity = launchConsent()

        activity.findViewById<android.view.View>(R.id.btnDecline).performClick()

        assertTrue(activity.isFinishing)
        assertFalse(ConsentManager.isAccepted(activity))
    }

    @Test
    fun `terms link opens the browser`() {
        val activity = launchConsent()

        activity.findViewById<android.view.View>(R.id.tvOpenTerms).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(activity.getString(R.string.terms_url), started?.data.toString())
    }

    @Test
    fun `policy link opens the browser`() {
        val activity = launchConsent()

        activity.findViewById<android.view.View>(R.id.tvOpenPolicy).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(activity.getString(R.string.policy_url), started?.data.toString())
    }
}
