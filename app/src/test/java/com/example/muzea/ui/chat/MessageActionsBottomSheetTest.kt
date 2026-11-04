package com.example.muzea.ui.chat

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.example.muzea.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Robolectric-тесты шторки действий: ряд иконок, обработка нажатия и секция
 * статуса прочтения.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MessageActionsBottomSheetTest {

    class HostFragment : Fragment(), MessageActionsBottomSheet.Listener {
        var action: Pair<String, String>? = null
        override fun onMessageAction(messageUuid: String, actionId: String) {
            action = messageUuid to actionId
        }
    }

    private lateinit var requestManager: RequestManager
    private lateinit var requestBuilder: RequestBuilder<android.graphics.drawable.Drawable>
    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        requestManager = mockk(relaxed = true)
        requestBuilder = mockk(relaxed = true)
        // Glide возвращает self-типы; relaxed-мок даёт для них несовместимый
        // подкласс — задаём цепочку явно (см. MessageAdapterTest).
        every { requestManager.load(any<String>()) } returns requestBuilder
        every { requestManager.load(any<Any>()) } returns requestBuilder
        every { requestBuilder.circleCrop() } returns requestBuilder
        every { requestBuilder.placeholder(any<Int>()) } returns requestBuilder
        every { requestBuilder.fallback(any<Int>()) } returns requestBuilder
        every { requestBuilder.error(any<Int>()) } returns requestBuilder
        every { requestBuilder.diskCacheStrategy(any()) } returns requestBuilder
        mockkStatic(Glide::class)
        every { Glide.with(any<View>()) } returns requestManager
        every { Glide.with(any<android.content.Context>()) } returns requestManager
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    }

    @After
    fun tearDown() {
        unmockkStatic(Glide::class)
    }

    private fun actionSpec(id: String) = MessageActionSpec(
        id = id, iconRes = android.R.drawable.ic_menu_edit, labelRes = R.string.action_copy
    )

    private fun showSheet(
        actions: List<MessageActionSpec>,
        rows: List<MessageStatusRow> = emptyList(),
        showStatus: Boolean = false
    ): Pair<HostFragment, MessageActionsBottomSheet> {
        val host = HostFragment()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()
        val sheet = MessageActionsBottomSheet.newInstance("m1", actions, rows, showStatus)
        host.childFragmentManager.beginTransaction().add(sheet, "sheet").commitNow()
        activity.supportFragmentManager.executePendingTransactions()
        host.childFragmentManager.executePendingTransactions()
        ShadowLooper.idleMainLooper()
        return host to sheet
    }

    private fun MessageActionsBottomSheet.find(id: Int): View = requireView().findViewById(id)

    @Test
    fun `newInstance stores its arguments`() {
        val sheet = MessageActionsBottomSheet.newInstance("m1", emptyList(), emptyList(), true)

        assertEquals("m1", sheet.arguments?.getString("message_uuid"))
        assertTrue(sheet.arguments?.getBoolean("show_status") == true)
    }

    @Test
    fun `empty actions hide the icon row`() {
        val (_, sheet) = showSheet(actions = emptyList())

        assertEquals(View.GONE, sheet.find(R.id.actionsScroll).visibility)
    }

    @Test
    fun `actions are rendered and clicking one notifies the listener`() {
        val (host, sheet) = showSheet(
            actions = listOf(actionSpec(MessageActionsBottomSheet.ACTION_REPLY))
        )

        val container = sheet.find(R.id.actionsContainer) as android.widget.LinearLayout
        assertEquals(View.VISIBLE, sheet.find(R.id.actionsScroll).visibility)
        assertEquals(1, container.childCount)

        container.getChildAt(0).performClick()
        ShadowLooper.idleMainLooper()

        assertEquals("m1" to MessageActionsBottomSheet.ACTION_REPLY, host.action)
    }

    @Test
    fun `status section is hidden when not requested`() {
        val (_, sheet) = showSheet(actions = emptyList(), showStatus = false)

        assertEquals(View.GONE, sheet.find(R.id.statusSection).visibility)
    }

    @Test
    fun `empty status shows the empty label`() {
        val (_, sheet) = showSheet(
            actions = listOf(actionSpec(MessageActionsBottomSheet.ACTION_COPY)),
            rows = emptyList(),
            showStatus = true
        )

        assertEquals(View.VISIBLE, sheet.find(R.id.statusSection).visibility)
        assertEquals(View.VISIBLE, sheet.find(R.id.tvEmpty).visibility)
        assertEquals(View.GONE, sheet.find(R.id.rowsScroll).visibility)
    }

    @Test
    fun `status rows are rendered with label and time`() {
        val rows = listOf(
            MessageStatusRow("Anna", null, "READ", "2026-01-01T10:00:00"),
            MessageStatusRow("Bob", null, "DELIVERED", null),
            MessageStatusRow("Carl", null, "FAILED", "2026-01-01T10:01:00"),
            MessageStatusRow("Dan", null, "SENDING", "2026-01-01T10:02:00"),
            MessageStatusRow("Eve", null, "SENT", "2026-01-01T10:03:00")
        )
        val (_, sheet) = showSheet(
            actions = listOf(actionSpec(MessageActionsBottomSheet.ACTION_COPY)),
            rows = rows,
            showStatus = true
        )

        val container = sheet.find(R.id.rowsContainer) as android.widget.LinearLayout
        assertEquals(5, container.childCount)
        assertEquals(View.GONE, sheet.find(R.id.tvEmpty).visibility)
        assertEquals(View.VISIBLE, sheet.find(R.id.rowsScroll).visibility)

        // У второй строки времени нет — метка времени скрыта.
        val secondTime = container.getChildAt(1).findViewById<android.widget.TextView>(R.id.tvTime)
        assertEquals(View.GONE, secondTime.visibility)
    }

    @Test
    fun `dismiss on empty view works`() {
        val (_, sheet) = showSheet(actions = emptyList(), showStatus = false)
        sheet.onDestroyView()
    }
}
