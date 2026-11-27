package com.example.muzea.ui.news

import android.view.View
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.example.muzea.databinding.ItemNewsCommentBinding
import com.example.muzea.domain.model.NewsComment
import com.example.muzea.utils.AvatarLoader
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NewsCommentsAdapterTest {

    private lateinit var root: LinearLayout
    private lateinit var author: TextView
    private lateinit var time: TextView
    private lateinit var content: TextView
    private lateinit var more: ImageButton
    private lateinit var avatar: ImageView
    private lateinit var binding: ItemNewsCommentBinding

    @Before
    fun setUp() {
        root = mockk(relaxed = true)
        author = mockk(relaxed = true)
        time = mockk(relaxed = true)
        content = mockk(relaxed = true)
        more = mockk(relaxed = true)
        avatar = mockk(relaxed = true)
        binding = mockk(relaxed = true)

        every { binding.root } returns root
        setField("tvCommentAuthor", author)
        setField("tvCommentTime", time)
        setField("tvCommentContent", content)
        setField("btnCommentMore", more)
        setField("ivCommentAvatar", avatar)
    }

    private fun setField(name: String, value: View) {
        val field = ItemNewsCommentBinding::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(binding, value)
    }

    private fun comment() = NewsComment(
        id = 4,
        newsId = 1,
        content = "Текст комментария",
        author = "bob",
        createdAt = "2026-01-01T00:00:00"
    )

    @Test
    fun `binds author time and content`() {
        val holder = NewsCommentsAdapter.CommentViewHolder(binding) {}
        holder.bind(comment())

        verify { author.setText("bob") }
        verify { content.setText("Текст комментария") }
        verify { time.setText(any<CharSequence>()) }
    }

    @Test
    fun `binds author avatar`() {
        mockkObject(AvatarLoader)
        every { AvatarLoader.load(any(), any()) } just Runs
        try {
            val item = comment().copy(authorAvatarUrl = "/api/avatars/bob/1.png")
            NewsCommentsAdapter.CommentViewHolder(binding) {}.bind(item)

            verify { AvatarLoader.load(avatar, "/api/avatars/bob/1.png") }
        } finally {
            unmockkObject(AvatarLoader)
        }
    }

    @Test
    fun `more button reports the comment`() {
        var selected: NewsComment? = null
        val holder = NewsCommentsAdapter.CommentViewHolder(binding) { selected = it }
        val item = comment()
        holder.bind(item)

        val listener = slot<View.OnClickListener>()
        verify { more.setOnClickListener(capture(listener)) }
        listener.captured.onClick(more)

        assertEquals(item, selected)
    }

    @Test
    fun `long press reports the comment`() {
        var selected: NewsComment? = null
        val holder = NewsCommentsAdapter.CommentViewHolder(binding) { selected = it }
        val item = comment()
        holder.bind(item)

        val listener = slot<View.OnLongClickListener>()
        verify { root.setOnLongClickListener(capture(listener)) }
        listener.captured.onLongClick(root)

        assertEquals(item, selected)
    }

    @Test
    fun `diff callback compares ids and contents`() {
        val callback = NewsCommentsAdapter.CommentDiffCallback()
        val item = comment()

        org.junit.Assert.assertTrue(callback.areItemsTheSame(item, item.copy(content = "other")))
        org.junit.Assert.assertFalse(callback.areItemsTheSame(item, item.copy(id = 5)))
        org.junit.Assert.assertTrue(callback.areContentsTheSame(item, item))
        org.junit.Assert.assertFalse(callback.areContentsTheSame(item, item.copy(content = "other")))
    }
}
