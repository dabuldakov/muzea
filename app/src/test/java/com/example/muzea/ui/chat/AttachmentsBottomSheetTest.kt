package com.example.muzea.ui.chat

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric-тест пикера вложений: иконки отдают тип и закрывают шторку. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AttachmentsBottomSheetTest {

    class HostFragment : Fragment(), AttachmentsBottomSheet.Listener {
        var kind: AttachmentsBottomSheet.Kind? = null
        override fun onAttachmentKind(kind: AttachmentsBottomSheet.Kind) {
            this.kind = kind
        }
    }

    private lateinit var activity: FragmentActivity
    private lateinit var host: HostFragment

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        host = HostFragment()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()
    }

    private fun sheet(): AttachmentsBottomSheet {
        val sheet = AttachmentsBottomSheet.newInstance()
        host.childFragmentManager.beginTransaction().add(sheet, "sheet").commitNow()
        activity.supportFragmentManager.executePendingTransactions()
        host.childFragmentManager.executePendingTransactions()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return sheet
    }

    @Test
    fun `photo icon reports PHOTO and dismisses`() {
        val sheet = sheet()

        sheet.requireView().findViewById<View>(com.example.muzea.R.id.btnPhoto).performClick()

        assertEquals(AttachmentsBottomSheet.Kind.PHOTO, host.kind)
        assertFalse(sheet.isVisible)
    }

    @Test
    fun `video audio and file icons report their kinds`() {
        val kinds = listOf(
            com.example.muzea.R.id.btnVideo to AttachmentsBottomSheet.Kind.VIDEO,
            com.example.muzea.R.id.btnAudio to AttachmentsBottomSheet.Kind.AUDIO,
            com.example.muzea.R.id.btnFile to AttachmentsBottomSheet.Kind.FILE
        )
        for ((id, expected) in kinds) {
            val sheet = sheet()
            sheet.requireView().findViewById<View>(id).performClick()
            assertEquals(expected, host.kind)
        }
    }
}
