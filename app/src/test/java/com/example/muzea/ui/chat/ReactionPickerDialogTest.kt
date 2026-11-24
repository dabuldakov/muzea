package com.example.muzea.ui.chat

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ReactionPickerDialogTest {

    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Muzea
    )

    @Test
    fun `picking emoji reports it and closes dialog`() {
        var picked: String? = null
        val message = message()

        ReactionPickerDialog.show(context, message) { _, emoji -> picked = emoji }
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val grid = findGrid(dialog.window!!.decorView as ViewGroup)
        val first = (0 until grid.childCount)
            .map { grid.getChildAt(it) }
            .filterIsInstance<TextView>()
            .first()

        first.performClick()

        assertEquals("👍", picked)
        assertEquals(false, dialog.isShowing)
    }

    private fun findGrid(root: ViewGroup): GridLayout {
        val stack = ArrayDeque<View>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val view = stack.removeLast()
            if (view is GridLayout) return view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) stack.add(view.getChildAt(i))
            }
        }
        error("GridLayout not found")
    }

    private fun message() = Message(
        messageUuid = "m-1",
        chatUuid = "c-1",
        senderId = 1L,
        senderUuid = "u-1",
        senderName = "Anna",
        senderAvatar = null,
        text = "hi",
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01T10:00:00",
        updatedAt = null,
        attachments = emptyList()
    )
}
