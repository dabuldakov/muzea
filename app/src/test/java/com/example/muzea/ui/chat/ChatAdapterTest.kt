package com.example.muzea.ui.chat

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.databinding.ItemChatBinding
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.MessagePreview
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.ChatTimeFormatter
import com.google.android.material.card.MaterialCardView
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты ChatAdapter без Robolectric.
 *
 * Строку проверяем на моке [ItemChatBinding]: поля data-binding — публичные
 * final-поля без геттеров, их подставляем рефлексией. Аватары не грузятся.
 */
class ChatAdapterTest {

    private lateinit var root: MaterialCardView
    private lateinit var title: TextView
    private lateinit var chatTime: TextView
    private lateinit var lastMessage: TextView
    private lateinit var unread: TextView
    private lateinit var avatar: ImageView
    private lateinit var binding: ItemChatBinding
    private lateinit var context: Context

    @Before
    fun setUp() {
        root = mockk(relaxed = true)
        title = mockk(relaxed = true)
        chatTime = mockk(relaxed = true)
        lastMessage = mockk(relaxed = true)
        unread = mockk(relaxed = true)
        avatar = mockk(relaxed = true)
        binding = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { binding.root } returns root
        setViewField("ivAvatar", avatar)
        setViewField("tvChatTitle", title)
        setViewField("tvChatTime", chatTime)
        setViewField("tvLastMessage", lastMessage)
        setViewField("tvUnreadCount", unread)
        every { root.context } returns context

        mockkObject(AvatarLoader)
        justRun { AvatarLoader.load(any(), any()) }
    }

    @After
    fun tearDown() {
        unmockkObject(AvatarLoader)
    }

    private fun setViewField(field: String, value: View) {
        val f = ItemChatBinding::class.java.getDeclaredField(field)
        f.isAccessible = true
        f.set(binding, value)
    }

    /**
     * В юнит-тестах android.jar — заглушка: поле `mObservers` у
     * `android.database.Observable` не инициализировано, поэтому любой
     * `notify*` падает с NPE. Подставляем пустой список наблюдателей.
     */
    private fun enableNotifications(adapter: RecyclerView.Adapter<*>) {
        val observable = RecyclerView.Adapter::class.java
            .getDeclaredField("mObservable").apply { isAccessible = true }.get(adapter)
        var cls: Class<*>? = observable.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField("mObservers")
                f.isAccessible = true
                f.set(observable, ArrayList<Any>())
                return
            } catch (e: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
    }

    private fun chat(
        uuid: String = "c-1",
        title: String? = "Title",
        avatarUrl: String? = "/a.png",
        lastMessage: MessagePreview? = null,
        unreadCount: Long? = null,
        peerDeleted: Boolean = false
    ) = Chat(
        chatUuid = uuid,
        chatType = null,
        title = title,
        avatarUrl = avatarUrl,
        createdAt = null,
        updatedAt = null,
        participantCount = null,
        lastMessage = lastMessage,
        unreadCount = unreadCount,
        peerDeleted = peerDeleted
    )

    private fun preview(
        text: String? = "hi",
        senderName: String? = "Anna",
        createdAt: String? = "2026-01-01T10:00:00"
    ) = MessagePreview(
        messageUuid = "m-1",
        text = text,
        senderId = 1L,
        senderName = senderName,
        createdAt = createdAt
    )

    private fun holder(onClick: (String) -> Unit = {}) =
        ChatAdapter.ChatViewHolder(binding, onClick)

    @Test
    fun `binds the chat title`() {
        holder().bind(chat(title = "Friends"))

        verify { title.setText("Friends") }
    }

    @Test
    fun `null title falls back to the default label`() {
        holder().bind(chat(title = null))

        verify { title.setText("Chat") }
    }

    @Test
    fun `last message with a sender is prefixed with the name`() {
        holder().bind(chat(lastMessage = preview(text = "hi", senderName = "Anna")))

        verify { lastMessage.setText("Anna: hi") }
    }

    @Test
    fun `last message without a sender is shown bare`() {
        holder().bind(chat(lastMessage = preview(text = "hi", senderName = null)))

        verify { lastMessage.setText("hi") }
    }

    @Test
    fun `missing last message shows the empty placeholder`() {
        every { context.getString(R.string.chat_empty) } returns "No messages yet"

        holder().bind(chat(lastMessage = null))

        verify { lastMessage.setText("No messages yet") }
    }

    @Test
    fun `blank last message text shows the empty placeholder`() {
        every { context.getString(R.string.chat_empty) } returns "No messages yet"

        holder().bind(chat(lastMessage = preview(text = "")))

        verify { lastMessage.setText("No messages yet") }
    }

    @Test
    fun `message with a timestamp shows the formatted time`() {
        holder().bind(chat(lastMessage = preview(createdAt = "2026-01-01T10:00:00")))

        verify { chatTime.setText(ChatTimeFormatter.format("2026-01-01T10:00:00")) }
        verify { chatTime.visibility = View.VISIBLE }
    }

    @Test
    fun `message without a timestamp hides the time`() {
        holder().bind(chat(lastMessage = preview(createdAt = null)))

        verify { chatTime.visibility = View.GONE }
    }

    @Test
    fun `positive unread count shows the badge`() {
        holder().bind(chat(unreadCount = 3L))

        verify { unread.setText("3") }
        verify { unread.visibility = View.VISIBLE }
    }

    @Test
    fun `zero unread count hides the badge`() {
        holder().bind(chat(unreadCount = 0L))

        verify { unread.visibility = View.GONE }
    }

    @Test
    fun `null unread count hides the badge`() {
        holder().bind(chat(unreadCount = null))

        verify { unread.visibility = View.GONE }
    }

    @Test
    fun `loads the chat avatar`() {
        holder().bind(chat(avatarUrl = "/avatar.png"))

        verify { AvatarLoader.load(avatar, "/avatar.png") }
    }

    @Test
    fun `clicking the row reports the chat uuid`() {
        var clicked: String? = null
        val holder = holder { clicked = it }
        holder.bind(chat(uuid = "chat-7"))

        val listener = slot<View.OnClickListener>()
        verify { root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(root)

        assertEquals("chat-7", clicked)
    }

    @Test
    fun `adapter starts empty`() {
        assertEquals(0, ChatAdapter { }.itemCount)
    }

    @Test
    fun `updateList publishes the first list synchronously`() {
        val adapter = ChatAdapter { }
        enableNotifications(adapter)
        adapter.updateList(listOf(chat(uuid = "a"), chat(uuid = "b")))

        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `onBindViewHolder binds the item at the requested position`() {
        val adapter = ChatAdapter { }
        enableNotifications(adapter)
        adapter.updateList(listOf(chat(uuid = "a", title = "Bond")))

        adapter.onBindViewHolder(ChatAdapter.ChatViewHolder(binding, { }), 0)

        verify { title.setText("Bond") }
    }

    @Test
    fun `clearItems on a fresh adapter leaves it empty`() {
        val adapter = ChatAdapter { }
        enableNotifications(adapter)
        adapter.clearItems()

        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `diff callback compares uuid and contents`() {
        val callback = ChatAdapter.ChatDiffCallback()
        val a = chat(uuid = "a")
        val b = chat(uuid = "b")
        val changed = chat(uuid = "a", title = "other")

        assertTrue(callback.areItemsTheSame(a, changed))
        assertFalse(callback.areItemsTheSame(a, b))
        assertTrue(callback.areContentsTheSame(a, chat(uuid = "a")))
        assertFalse(callback.areContentsTheSame(a, changed))
    }

    @Test
    fun `chat with a deleted peer shows the deleted user title`() {
        every { context.getString(R.string.deleted_user) } returns "Deleted user"

        holder().bind(chat(title = null, peerDeleted = true))

        verify { title.setText("Deleted user") }
    }
}
