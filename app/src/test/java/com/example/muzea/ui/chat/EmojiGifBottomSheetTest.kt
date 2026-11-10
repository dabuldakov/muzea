package com.example.muzea.ui.chat

import android.app.Application
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric-тест шторки эмодзи/GIF. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EmojiGifBottomSheetTest {

    class Host : Fragment(), EmojiGifBottomSheet.Listener {
        var emoji: String? = null
        var gifOpened = false
        override fun onEmojiSelected(emoji: String) {
            this.emoji = emoji
        }

        override fun onOpenGif() {
            gifOpened = true
        }
    }

    private fun open(): Triple<FragmentActivity, Host, EmojiGifBottomSheet> {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val host = Host()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()
        val sheet = EmojiGifBottomSheet.newInstance()
        host.childFragmentManager.beginTransaction().add(sheet, "sheet").commitNow()
        activity.supportFragmentManager.executePendingTransactions()
        host.childFragmentManager.executePendingTransactions()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return Triple(activity, host, sheet)
    }

    @Test
    fun `tapping an emoji reports it`() {
        val (_, host, sheet) = open()
        val grid = sheet.requireView()
            .findViewById<android.widget.GridLayout>(com.example.muzea.R.id.emojiGrid)
        val cell = (0 until grid.childCount)
            .map { grid.getChildAt(it) as TextView }
            .first { it.text == "😀" }

        cell.performClick()

        assertEquals("😀", host.emoji)
    }

    @Test
    fun `gif button asks the host to open gif picker`() {
        val (_, host, sheet) = open()

        sheet.requireView().findViewById<android.view.View>(com.example.muzea.R.id.btnOpenGif)
            .performClick()

        assertTrue(host.gifOpened)
    }
}
