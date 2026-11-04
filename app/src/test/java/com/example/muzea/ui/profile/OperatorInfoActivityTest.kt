package com.example.muzea.ui.profile

import android.content.Intent
import android.view.View
import android.widget.TextView
import com.example.muzea.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric-тесты сведений об операторе персональных данных: контакты и
 * открытие ссылок на правовые документы.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class OperatorInfoActivityTest {

    private fun launchOperator() =
        Robolectric.buildActivity(OperatorInfoActivity::class.java).setup().get()

    @Test
    fun `email row shows the operator address`() {
        val activity = launchOperator()

        assertEquals(
            activity.getString(R.string.operator_email),
            activity.findViewById<TextView>(R.id.tvOperatorEmail).text.toString()
        )
    }

    @Test
    fun `tapping email opens a mailto link`() {
        val activity = launchOperator()

        activity.findViewById<View>(R.id.tvOperatorEmail).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("mailto:${activity.getString(R.string.operator_email)}", started?.data.toString())
    }

    @Test
    fun `tapping phone opens a tel link`() {
        val activity = launchOperator()

        activity.findViewById<View>(R.id.tvOperatorPhone).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("tel:${activity.getString(R.string.operator_phone)}", started?.data.toString())
    }

    @Test
    fun `tapping policy opens the privacy page`() {
        val activity = launchOperator()

        activity.findViewById<View>(R.id.tvOperatorPolicy).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://muzea.su/privacy", started?.data.toString())
    }

    @Test
    fun `tapping terms opens the terms page`() {
        val activity = launchOperator()

        activity.findViewById<View>(R.id.tvOperatorTerms).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://muzea.su/terms", started?.data.toString())
    }

    @Test
    fun `all document links resolve`() {
        val activity = launchOperator()

        activity.findViewById<View>(R.id.tvOperatorPolicy).performClick()
        assertTrue(shadowOf(activity).nextStartedActivity != null)
        activity.findViewById<View>(R.id.tvOperatorTerms).performClick()
        assertTrue(shadowOf(activity).nextStartedActivity != null)
    }
}
