package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PinnedBarViewTest {

    private val context: Context = androidx.appcompat.view.ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Muzea
    )

    private fun message(uuid: String, text: String = "привет") = Message(
        messageUuid = uuid,
        messageId = 1,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = "u1",
        senderName = "Anna",
        senderAvatar = null,
        text = text,
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = true,
        createdAt = "2026-01-01T10:00:00",
        updatedAt = null
    )

    @Test
    fun `empty pins hide the bar`() {
        val bar = View(context)
        val subject = PinnedBarView(context, bar, View(context), View(context),
            TextView(context), TextView(context), {}, {})

        subject.render(emptyList())

        assertEquals(View.GONE, bar.visibility)
    }

    @Test
    fun `single pin shows title and preview`() {
        val bar = View(context)
        val title = TextView(context)
        val preview = TextView(context)
        val subject = PinnedBarView(context, bar, View(context), View(context),
            title, preview, {}, {})

        subject.render(listOf(message("m1", text = "закреплённое")))

        assertEquals(View.VISIBLE, bar.visibility)
        assertEquals(context.getString(R.string.pinned_message_title), title.text.toString())
        assertTrue(preview.text.toString().contains("закреплённое"))
    }

    @Test
    fun `several pins show the counter`() {
        val bar = View(context)
        val title = TextView(context)
        val subject = PinnedBarView(context, bar, View(context), View(context),
            title, TextView(context), {}, {})

        subject.render(listOf(message("m1"), message("m2")))

        assertEquals(context.getString(R.string.pinned_messages_count, 2), title.text.toString())
    }

    @Test
    fun `unpin and open report the first pin`() {
        val bar = View(context)
        val content = View(context)
        val unpin = View(context)
        var unpinned = 0
        var opened: String? = null
        val subject = PinnedBarView(context, bar, content, unpin,
            TextView(context), TextView(context), { unpinned++ }, { opened = it })
        subject.setup()
        subject.render(listOf(message("m7")))

        unpin.performClick()
        content.performClick()

        assertEquals(1, unpinned)
        assertEquals("m7", opened)
    }
}
