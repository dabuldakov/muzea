package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.domain.model.MessageReaction
import com.example.muzea.domain.model.ReactionUser
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DetailsSectionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        context = androidx.appcompat.view.ContextThemeWrapper(base, R.style.Theme_Muzea)
        mockkObject(AvatarLoader)
        justRun { AvatarLoader.load(any(), any()) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun content(): View = LayoutInflater.from(context)
        .inflate(R.layout.popup_message_actions, null, false)

    private fun row(uuid: String, name: String, status: String = "READ") = MessageStatusRow(
        userUuid = uuid,
        name = name,
        avatarUrl = null,
        status = status,
        at = "2026-01-01T10:00:00"
    )

    private fun reaction(uuid: String, emoji: String) = MessageReaction(
        emoji = emoji,
        count = 1,
        mine = false,
        users = listOf(ReactionUser(uuid, "2026-01-01T10:00:00"))
    )

    @Test
    fun `details render rows with reactions and report height`() {
        val content = content()
        var headerClicks = 0
        var rowClicks = 0
        val section = DetailsSection(
            context = context,
            content = content,
            density = context.resources.displayMetrics.density,
            onHeaderClick = { headerClicks++ },
            onRowClick = { rowClicks++ }
        )

        section.render(
            statusRows = listOf(row("u1", "Anna"), row("u2", "Bob", "DELIVERED")),
            reactions = listOf(reaction("u1", "👍")),
            reactionRows = emptyList(),
            showStatus = true
        )

        assertTrue(section.isVisible)
        assertEquals(View.VISIBLE, content.findViewById<View>(R.id.statusSection).visibility)
        val rows = content.findViewById<LinearLayout>(R.id.statusRows)
        assertEquals(2, rows.childCount)
        val rowPx = (DetailsSection.STATUS_ROW_DP * context.resources.displayMetrics.density).toInt()
        assertEquals(rowPx * 2, section.listHeight)

        content.findViewById<View>(R.id.readersRow).performClick()
        assertEquals(1, headerClicks)

        rows.getChildAt(0).performClick()
        assertEquals(1, rowClicks)
    }

    @Test
    fun `details stay visible with an empty state`() {
        val content = content()
        val section = DetailsSection(context, content, 1f, {}, {})

        section.render(
            statusRows = emptyList(),
            reactions = emptyList(),
            reactionRows = emptyList(),
            showStatus = true
        )

        assertTrue(section.isVisible)
        assertEquals(View.VISIBLE, content.findViewById<View>(R.id.statusSection).visibility)
        val rows = content.findViewById<LinearLayout>(R.id.statusRows)
        assertEquals(1, rows.childCount)
        assertEquals(
            context.getString(R.string.details_empty_reactions),
            (rows.getChildAt(0) as android.widget.TextView).text.toString()
        )
    }

    @Test
    fun `unknown author gets its own row`() {
        val content = content()
        val section = DetailsSection(context, content, 1f, {}, {})

        section.render(
            statusRows = emptyList(),
            reactions = listOf(reaction("u9", "🔥")),
            reactionRows = listOf(row("u9", "Участник", MessageDetailsRows.STATUS_REACTED)),
            showStatus = false
        )

        assertTrue(section.isVisible)
        assertEquals(1, content.findViewById<LinearLayout>(R.id.statusRows).childCount)
    }
}
