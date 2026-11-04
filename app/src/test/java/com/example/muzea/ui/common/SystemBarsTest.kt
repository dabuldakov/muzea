package com.example.muzea.ui.common

import android.app.Activity
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Системные панели настраиваются для обеих схем отступа (с нижним и без). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SystemBarsTest {

    @Test
    fun `apply configures bars with and without the bottom inset`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        SystemBars.apply(activity, applyBottomInset = true)
        SystemBars.apply(activity, applyBottomInset = false)

        // Прозрачные панели edge-to-edge без контрастной подложки.
        assertFalse(activity.window.isNavigationBarContrastEnforced)
    }
}
