package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MessageLongPressTest {

    private val message = Message(
        messageUuid = "m1",
        messageId = 1,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = "u1",
        senderName = "Morris",
        senderAvatar = null,
        text = "https://countrymonitoring.ru/",
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-11-12T16:19:18",
        updatedAt = null
    )

    private fun view(): TextView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TextView(context)
    }

    private fun down(): MotionEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0)

    private fun up(): MotionEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 10f, 10f, 0)

    @Test
    fun `long press opens message actions`() {
        val text = view()
        var opened = 0
        bindLongPress(text, message) { opened++ }

        text.dispatchTouchEvent(down())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))

        assertEquals(1, opened)
    }

    @Test
    fun `short tap is left to the normal click`() {
        val text = view()
        var opened = 0
        bindLongPress(text, message) { opened++ }

        text.dispatchTouchEvent(down())
        text.dispatchTouchEvent(up())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))

        assertEquals(0, opened)
    }

    @Test
    fun `long press is not repeated on touch up`() {
        val text = view()
        var opened = 0
        bindLongPress(text, message) { opened++ }

        text.dispatchTouchEvent(down())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        text.dispatchTouchEvent(up())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))

        assertEquals(1, opened)
    }
}
