package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ChargeMeterTest {

    private val context: Context = androidx.appcompat.view.ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), com.example.muzea.R.style.Theme_Muzea
    )

    @Test
    fun `reset hides the meter and restores the button`() {
        val bar = View(context)
        val progress = ProgressBar(context)
        val percent = TextView(context)
        val burst = ChargeBurstView(context)
        val button = View(context)
        val meter = ChargeMeter(
            bar, progress, percent, burst, button,
            chargeFormat = { "charge $it" },
            chargeZero = "zero",
            onVibrate = {}
        )

        meter.reset()

        assertEquals(View.GONE, bar.visibility)
        assertEquals(0, progress.progress)
        assertEquals("zero", percent.text.toString())
        assertEquals(1f, button.scaleX)
        assertEquals(1f, button.scaleY)
    }

    @Test
    fun `render fills the meter and scales the button`() {
        val bar = View(context)
        val progress = ProgressBar(context).apply { max = 100 }
        val percent = TextView(context)
        val burst = ChargeBurstView(context)
        val button = View(context)
        var level = -1f
        val meter = ChargeMeter(
            bar, progress, percent, burst, button,
            chargeFormat = { "charge $it" },
            chargeZero = "zero",
            onVibrate = { level = it }
        )

        meter.render(10_000L)

        assertEquals(View.VISIBLE, bar.visibility)
        assertEquals("charge 39", percent.text.toString())
        assertTrue(button.scaleX >= 1f)
        assertTrue(level > 0f)
    }
}
