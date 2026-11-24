package com.example.muzea.ui.chat

import android.app.Dialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import androidx.appcompat.view.ContextThemeWrapper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ChatDialogsTest {

    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Muzea
    )

    @Test
    fun `confirm delete calls callback`() {
        var confirmed = false
        ChatDialogs.confirmDeleteMessage(context) { confirmed = true }
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = latestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(confirmed)
    }

    @Test
    fun `confirm block calls callback`() {
        var blocked = false
        ChatDialogs.confirmBlock(context) { blocked = true }
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = latestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(blocked)
    }

    @Test
    fun `confirm delete selected calls callback`() {
        var confirmed = false
        ChatDialogs.confirmDeleteSelected(context) { confirmed = true }
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = latestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(confirmed)
    }

    @Test
    fun `forward comment sends trimmed text`() {
        var sent: String? = "not-called"
        ChatDialogs.forwardComment(context, android.view.LayoutInflater.from(context)) { sent = it }
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = latestDialog()
        val root = dialog.findViewById<ViewGroup>(android.R.id.content)
            ?: (dialog.window?.decorView as ViewGroup)
        val input = root.findViewById<EditText>(R.id.etForwardComment)
        input.setText("  привет  ")
        root.findViewById<View>(R.id.btnForwardSend).performClick()

        assertEquals("привет", sent)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun `blank forward comment becomes null`() {
        var sent: String? = "not-called"
        ChatDialogs.forwardComment(context, android.view.LayoutInflater.from(context)) { sent = it }
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = latestDialog()
        val root = dialog.findViewById<ViewGroup>(android.R.id.content)
            ?: (dialog.window?.decorView as ViewGroup)
        root.findViewById<View>(R.id.btnForwardSend).performClick()

        assertEquals(null, sent)
    }

    private fun latestDialog(): Dialog {
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        return dialog
    }
}
