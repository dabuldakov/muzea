package com.example.muzea.ui.chat

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.utils.AvatarLoader
import io.mockk.justRun
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Карточка действий: раскрытие «Деталей» и подмена их содержимого свежими
 * квитанциями, пока окно не закрыто.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MessageActionsPopupTest {

    private lateinit var activity: Activity
    private lateinit var context: Context
    private lateinit var anchor: View

    @Before
    fun setUp() {
        mockkObject(AvatarLoader)
        justRun { AvatarLoader.load(any(), any()) }
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_Muzea
        )
        anchor = View(context)
        activity.setContentView(anchor)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun row(uuid: String, name: String) = MessageStatusRow(
        userUuid = uuid,
        name = name,
        avatarUrl = null,
        status = "READ",
        at = "2026-01-01T10:00:00"
    )

    private fun action() = MessageActionSpec(
        MessageActionsBottomSheet.ACTION_COPY,
        R.drawable.ic_action_copy,
        R.string.action_copy
    )

    private fun popup(onDetailsExpanded: () -> Unit = {}) = MessageActionsPopup(
        context = context,
        messageUuid = "m1",
        actions = listOf(action()),
        statusRows = listOf(row("u1", "Anna")),
        reactions = emptyList(),
        reactionRows = emptyList(),
        showStatus = true,
        onAction = {},
        onReaction = {},
        onDetailsExpanded = onDetailsExpanded
    )

    /** Разметка карточки: наружу она не отдаётся, для проверки берём полем. */
    private fun contentOf(popup: MessageActionsPopup): View {
        val field = MessageActionsPopup::class.java.getDeclaredField("content")
        field.isAccessible = true
        return field.get(popup) as View
    }

    @Test
    fun `expanding details asks for fresh receipts`() {
        var asked = 0
        val popup = popup(onDetailsExpanded = { asked++ })
        popup.show(anchor, 10, 10)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(popup.isShowing())
        val content = contentOf(popup)
        content.findViewById<View>(R.id.readersRow).performClick()

        assertEquals(1, asked)
    }

    @Test
    fun `refresh details replaces rows without closing the card`() {
        val popup = popup()
        popup.show(anchor, 10, 10)
        shadowOf(Looper.getMainLooper()).idle()
        val content = contentOf(popup)
        content.findViewById<View>(R.id.readersRow).performClick()

        popup.refreshDetails(
            statusRows = listOf(row("u1", "Anna"), row("u2", "Bob")),
            reactionRows = emptyList()
        )

        val rows = content.findViewById<LinearLayout>(R.id.statusRows)
        assertEquals(2, rows.childCount)
        assertEquals(
            View.VISIBLE,
            content.findViewById<ScrollView>(R.id.statusScroll).visibility
        )
        assertTrue(popup.isShowing())
    }

    @Test
    fun `refresh details keeps a collapsed list collapsed`() {
        val popup = popup()
        popup.show(anchor, 10, 10)
        shadowOf(Looper.getMainLooper()).idle()
        val content = contentOf(popup)

        popup.refreshDetails(listOf(row("u1", "Anna")), emptyList())

        assertEquals(1, content.findViewById<LinearLayout>(R.id.statusRows).childCount)
        assertEquals(
            View.GONE,
            content.findViewById<ScrollView>(R.id.statusScroll).visibility
        )
        assertTrue(popup.isShowing())
    }
}
