package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.domain.model.Message
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SelectionControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun message(uuid: String) = Message(
        messageUuid = uuid,
        messageId = 1,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = "me",
        senderName = null,
        senderAvatar = null,
        text = "hi",
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01T10:00:00",
        updatedAt = null
    )

    @Test
    fun `enter selects the message and shows the bar`() {
        val adapter = mockk<MessageAdapter>(relaxed = true)
        val bar = View(context)
        val composer = View(context)
        val count = TextView(context)
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = Unit
        }
        val controller = SelectionController(
            adapter, bar, composer, count, callback,
            messages = { listOf(message("m1"), message("m2")) },
            countLabel = { "выбрано $it" },
            onDelete = {}, onForward = {}
        )

        controller.enter(message("m1"))

        assertTrue(controller.isActive)
        assertEquals(View.VISIBLE, bar.visibility)
        assertEquals(View.GONE, composer.visibility)
        assertEquals("выбрано 1", count.text.toString())
        assertTrue(callback.isEnabled)
        verify { adapter.setSelection(setOf("m1")) }
    }

    @Test
    fun `deselecting the last message exits the mode`() {
        val adapter = mockk<MessageAdapter>(relaxed = true)
        val bar = View(context)
        val composer = View(context)
        val count = TextView(context)
        val controller = SelectionController(
            adapter, bar, composer, count, object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() = Unit
            },
            messages = { listOf(message("m1")) },
            countLabel = { "выбрано $it" },
            onDelete = {}, onForward = {}
        )

        controller.enter(message("m1"))
        controller.toggle(message("m1"))

        assertFalse(controller.isActive)
        assertEquals(View.GONE, bar.visibility)
        assertEquals(View.VISIBLE, composer.visibility)
    }

    @Test
    fun `selected items are filtered from the list`() {
        val controller = SelectionController(
            mockk(relaxed = true), View(context), View(context), TextView(context),
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() = Unit
            },
            messages = { listOf(message("m1"), message("m2"), message("m3")) },
            countLabel = { "" }, onDelete = {}, onForward = {}
        )

        controller.enter(message("m1"))
        controller.toggle(message("m3"))

        assertEquals(listOf("m1", "m3"), controller.selectedItems().map { it.messageUuid })
    }

    @Test
    fun `bar buttons call their actions`() {
        var deleted = 0
        var forwarded = 0
        val deleteButton = View(context)
        val forwardButton = View(context)
        val closeButton = View(context)
        val controller = SelectionController(
            mockk(relaxed = true), View(context), View(context), TextView(context),
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() = Unit
            },
            messages = { emptyList() }, countLabel = { "" },
            onDelete = { deleted++ }, onForward = { forwarded++ }
        )
        controller.setup(deleteButton, forwardButton, closeButton)

        deleteButton.performClick()
        forwardButton.performClick()
        closeButton.performClick()

        assertEquals(1, deleted)
        assertEquals(1, forwarded)
    }
}
