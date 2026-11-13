package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ComposerBannerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class Views(
        val banner: View,
        val cancel: ImageButton,
        val title: TextView,
        val preview: TextView,
        val input: EditText,
        var focused: Int = 0
    )

    private fun views(): Views {
        val banner = LinearLayout(context)
        val cancel = ImageButton(context)
        val title = TextView(context)
        val preview = TextView(context)
        val input = EditText(context)
        return Views(banner, cancel, title, preview, input)
    }

    private fun banner(v: Views) = ComposerBanner(
        context = context,
        banner = v.banner,
        cancelButton = v.cancel,
        titleView = v.title,
        previewView = v.preview,
        input = v.input,
        myUserUuid = { "me" },
        requestInput = { v.focused++ }
    )

    private fun message(
        uuid: String,
        text: String? = "привет",
        sender: String = "other"
    ) = Message(
        messageUuid = uuid,
        messageId = 1,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = sender,
        senderName = "Anna",
        senderAvatar = null,
        text = text,
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01T10:00:00",
        updatedAt = null
    )

    @Test
    fun `reply shows banner with sender and preview`() {
        val v = views()
        banner(v).render(replyTo = message("m1"), editing = null)

        assertEquals(View.VISIBLE, v.banner.visibility)
        assertTrue(v.title.text.toString().contains("Anna"))
        assertEquals("привет", v.preview.text.toString())
        assertEquals(1, v.focused)
    }

    @Test
    fun `no reply hides banner`() {
        val v = views()
        banner(v).render(replyTo = null, editing = null)

        assertEquals(View.GONE, v.banner.visibility)
    }

    @Test
    fun `editing fills the input and focuses once per message`() {
        val v = views()
        val banner = banner(v)
        val editing = message("m1", text = "правка", sender = "me")

        banner.render(replyTo = null, editing = editing)
        assertEquals("правка", v.input.text.toString())
        assertEquals(1, v.focused)

        banner.render(replyTo = null, editing = editing)
        assertEquals(1, v.focused)
    }

    @Test
    fun `reply to a new message requests input once`() {
        val v = views()
        val banner = banner(v)
        val first = message("m1")

        banner.render(replyTo = first, editing = null)
        banner.render(replyTo = first, editing = null)
        assertEquals(1, v.focused)

        banner.render(replyTo = message("m2"), editing = null)
        assertEquals(2, v.focused)
    }
}
