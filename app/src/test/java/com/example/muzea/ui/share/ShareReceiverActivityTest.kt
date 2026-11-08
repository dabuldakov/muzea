package com.example.muzea.ui.share

import android.content.Intent
import android.os.Looper
import com.example.muzea.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric+Hilt тест приёмника «Поделиться»: без сессии ведёт на вход. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class ShareReceiverActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `shared text without a session opens login`() {
        val intent = Intent(
            org.robolectric.RuntimeEnvironment.getApplication(),
            ShareReceiverActivity::class.java
        ).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "https://muzea.su")
        }

        val activity = Robolectric.buildActivity(ShareReceiverActivity::class.java, intent)
            .setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivity
        assertTrue(started.component?.className!!.endsWith("LoginActivity"))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `non share intent is ignored`() {
        val intent = Intent(
            org.robolectric.RuntimeEnvironment.getApplication(),
            ShareReceiverActivity::class.java
        )
        val activity = Robolectric.buildActivity(ShareReceiverActivity::class.java, intent)
            .setup().get()

        val started = shadowOf(activity).nextStartedActivity
        assertTrue(started.component?.className!!.endsWith("LoginActivity"))
    }
}
