package com.example.muzea.ui.chat

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric-тест анимации удаления сообщения: схлопывание и сброс. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MessageItemAnimatorTest {

    private class Holder(view: View) : RecyclerView.ViewHolder(view)

    @Test
    fun `animateRemove collapses the bubble and resets it`() {
        val view = FrameLayout(org.robolectric.RuntimeEnvironment.getApplication())
        val animator = MessageItemAnimator()

        assertTrue(animator.animateRemove(Holder(view)))

        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertEquals(1f, view.alpha)
        assertEquals(1f, view.scaleX)
        assertEquals(1f, view.scaleY)
    }
}
