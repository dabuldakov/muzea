package com.example.muzea.ui.chat

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Robolectric-тест визуализации заряда: установка силы, burst и очистка. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ChargeBurstViewTest {

    @Test
    fun `power is clamped and burst does not crash`() {
        val view = ChargeBurstView(RuntimeEnvironment.getApplication())

        view.updatePower(150)
        assertEquals(100, view.power)

        view.updatePower(-5)
        assertEquals(0, view.power)

        view.updatePower(60)
        view.burst(60)
        view.clear()
        assertEquals(0, view.power)
        view.release()
    }
}
