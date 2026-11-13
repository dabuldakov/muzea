package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.utils.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ReactionsSectionTest {

    private fun context(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return androidx.appcompat.view.ContextThemeWrapper(base, R.style.Theme_Muzea)
    }

    private fun content(context: Context): View =
        LayoutInflater.from(context).inflate(R.layout.popup_message_actions, null, false)

    private val recent = listOf(
        "👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏", "🙏", "💯", "😍", "🤔", "😡"
    )
    private val all = recent + listOf("🥰", "😎", "🤝", "✅", "❌", "👀", "🤯", "😴", "💜", "⚡", "🚀")

    @Test
    fun `strip shows at most eleven and grid shows all`() {
        val context = context()
        val view = content(context)
        val section = ReactionsSection(context, view, 1f, onReaction = {}, animate = {})

        section.render(recent = recent, all = all)

        assertEquals(11, view.findViewById<LinearLayout>(R.id.recentReactions).childCount)
        assertEquals(all.size, view.findViewById<GridLayout>(R.id.fullReactions).childCount)
    }

    @Test
    fun `arrow replaces strip with the grid and animates height`() {
        val context = context()
        val view = content(context)
        var fraction: ((Float) -> Unit)? = null
        val section = ReactionsSection(
            context, view, 1f, onReaction = {}, animate = { fraction = it }
        )
        section.render(recent = recent, all = all)

        view.findViewById<View>(R.id.btnExpandReactions).performClick()

        assertEquals(View.GONE, view.findViewById<View>(R.id.reactionsStrip).visibility)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.fullReactions).visibility)
        assertEquals(true, section.isExpanded)
        assertNotNull(fraction)

        fraction!!.invoke(1f)
        val grid = view.findViewById<GridLayout>(R.id.fullReactions)
        // 24 реакции в 7 колонок — 4 ряда по 38 dp.
        assertEquals(4 * 38, grid.layoutParams.height)
    }

    @Test
    fun `tapping an emoji reports it`() {
        val context = context()
        val view = content(context)
        var picked: String? = null
        val section = ReactionsSection(
            context, view, 1f, onReaction = { picked = it }, animate = {}
        )
        section.render(recent = recent, all = all)

        view.findViewById<LinearLayout>(R.id.recentReactions).getChildAt(0).performClick()

        assertEquals("👍", picked)
    }
}
