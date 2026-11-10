package com.example.muzea.ui.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ChatDraftsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `draft survives until cleared`() {
        ChatDrafts.save(context, "chat-1", "привет")
        assertEquals("привет", ChatDrafts.get(context, "chat-1"))

        ChatDrafts.clear(context, "chat-1")
        assertNull(ChatDrafts.get(context, "chat-1"))
    }

    @Test
    fun `blank draft removes the stored text`() {
        ChatDrafts.save(context, "chat-1", "текст")
        ChatDrafts.save(context, "chat-1", "  ")

        assertNull(ChatDrafts.get(context, "chat-1"))
    }
}
