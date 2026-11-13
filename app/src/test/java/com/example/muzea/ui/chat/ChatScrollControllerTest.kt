package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ChatScrollControllerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun recycler(count: Int): RecyclerView {
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val view = TextView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 100
                        )
                    }
                    return object : RecyclerView.ViewHolder(view) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    (holder.itemView as TextView).text = "m$position"
                }

                override fun getItemCount(): Int = count
            }
        }
        list.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 1000, 1000)
        shadowOf(Looper.getMainLooper()).idle()
        return list
    }

    private fun controller(
        list: RecyclerView,
        button: View,
        count: Int,
        unread: () -> Int?,
        clear: () -> Unit
    ) = ChatScrollController(
        recyclerView = list,
        button = button,
        messageCount = { count },
        unreadAnchor = unread,
        clearUnreadAnchor = clear,
        labelRes = { if (it) 1 else 2 },
        label = { "label$it" }
    )

    @Test
    fun `button is visible off the bottom and hides at the bottom`() {
        val list = recycler(20)
        val button = View(context)
        val controller = controller(list, button, 20, { null }, {})

        controller.update()
        assertEquals(View.VISIBLE, button.visibility)
        assertEquals("label2", button.contentDescription)

        list.scrollBy(0, 100_000)
        shadowOf(Looper.getMainLooper()).idle()
        controller.update()
        assertEquals(View.GONE, button.visibility)
    }

    @Test
    fun `click scrolls to the unread message and clears the anchor`() {
        val list = recycler(20)
        val button = View(context)
        var unread: Int? = 12
        var cleared = false
        val controller = controller(list, button, 20, { unread }, { cleared = true; unread = null })
        controller.setup()

        button.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        list.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 1000, 1000)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(cleared)
        assertNull(unread)
        val layoutManager = list.layoutManager as LinearLayoutManager
        assertTrue(layoutManager.findFirstVisibleItemPosition() <= 12)
        assertTrue(layoutManager.findLastVisibleItemPosition() >= 12)
    }

    @Test
    fun `button shows unread label when there is an anchor`() {
        val list = recycler(20)
        val button = View(context)
        val controller = controller(list, button, 20, { 5 }, {})

        controller.update()

        assertEquals("label1", button.contentDescription)
    }

    @Test
    fun `empty chat hides the button`() {
        val list = recycler(0)
        val button = View(context)
        val controller = controller(list, button, 0, { null }, {})

        controller.update()

        assertEquals(View.GONE, button.visibility)
    }
}
