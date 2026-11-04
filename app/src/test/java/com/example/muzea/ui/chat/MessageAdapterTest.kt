package com.example.muzea.ui.chat

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.data.repository.LinkPreviewStore
import com.example.muzea.databinding.ItemMessageIncomingBinding
import com.example.muzea.databinding.ItemMessageOutgoingBinding
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.utils.AvatarLoader
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM-тесты (без Robolectric) самого [MessageAdapter]: типы строк, создание и
 * привязка держателей, аудио-состояние, updateList и DiffUtil.
 *
 * Robolectric-песочница не даёт покрытия JaCoCo, поэтому инфляция binding'ов
 * подменена, а вьюхи подставлены в их final-поля рефлексией (см.
 * [MessageAdapterFixtures]). Список кладём первым submitList — AsyncListDiffer
 * обновляет currentList синхронно на первой вставке, без главного лупера.
 */
class MessageAdapterTest {

    private lateinit var fx: MessageAdapterFixtures
    private lateinit var parent: ViewGroup
    private val linkPreviews = mockk<LinkPreviewStore>(relaxed = true)

    @Before
    fun setUp() {
        fx = MessageAdapterFixtures()
        parent = mockk(relaxed = true)
        every { parent.context } returns fx.context

        mockkObject(AvatarLoader)
        justRun { AvatarLoader.load(any(), any()) }

        mockkStatic(ItemMessageIncomingBinding::class)
        every { ItemMessageIncomingBinding.inflate(any(), any(), any()) } returns fx.incomingBinding
        mockkStatic(ItemMessageOutgoingBinding::class)
        every { ItemMessageOutgoingBinding.inflate(any(), any(), any()) } returns fx.outgoingBinding
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun message(
        uuid: String = "m1",
        senderUuid: String? = "other",
        senderName: String? = "Anna",
        text: String? = "hi",
        messageType: String? = "TEXT",
        replyToMessageUuid: String? = null,
        isDeleted: Boolean = false,
        isEdited: Boolean = false,
        createdAt: String? = "2026-01-01T10:00:00",
        attachments: List<MessageAttachment> = emptyList()
    ) = Message(
        messageUuid = uuid,
        chatUuid = "chat-1",
        senderId = 1L,
        senderUuid = senderUuid,
        senderName = senderName,
        senderAvatar = null,
        text = text,
        messageType = messageType,
        replyToMessageUuid = replyToMessageUuid,
        isEdited = isEdited,
        isDeleted = isDeleted,
        isPinned = false,
        createdAt = createdAt,
        updatedAt = null,
        attachments = attachments
    )

    private fun adapter(myUserUuid: String? = "me"): MessageAdapter {
        val adapter = MessageAdapter(myUserUuid, linkPreviews, {}, { null }, {}, { _, _ -> })
        patchObservers(adapter)
        return adapter
    }

    /**
     * В обычном JVM-тесте `android.database.Observable` — заглушка, поэтому
     * список наблюдателей RecyclerView не инициализируется, и любой
     * `notifyItem*` падает с NPE. Подставляем пустой список, чтобы вызовы
     * уведомлений были безвредными.
     */
    private fun patchObservers(adapter: RecyclerView.Adapter<*>) {
        val observableField = RecyclerView.Adapter::class.java.getDeclaredField("mObservable")
        observableField.isAccessible = true
        val observable = observableField.get(adapter)!!
        val observersField = android.database.Observable::class.java.getDeclaredField("mObservers")
        observersField.isAccessible = true
        observersField.set(observable, ArrayList<Any>())
    }

    @Test
    fun `getItemViewType separates mine from others`() {
        val adapter = adapter()
        adapter.submitList(
            listOf(
                message(uuid = "mine", senderUuid = "me"),
                message(uuid = "other", senderUuid = "other")
            )
        )

        assertEquals(1, adapter.getItemViewType(0))
        assertEquals(0, adapter.getItemViewType(1))
    }

    @Test
    fun `getItemViewType treats everyone as other without my uuid`() {
        val adapter = adapter(myUserUuid = null)
        adapter.submitList(listOf(message(uuid = "mine", senderUuid = "me")))

        assertEquals(0, adapter.getItemViewType(0))
    }

    @Test
    fun `getItemViewType treats a message without sender as incoming`() {
        val adapter = adapter()
        adapter.submitList(listOf(message(uuid = "x", senderUuid = null)))

        assertEquals(0, adapter.getItemViewType(0))
    }

    @Test
    fun `adapter creates both view holder types`() {
        val adapter = adapter()

        val outgoing = adapter.onCreateViewHolder(parent, 1)
        val incoming = adapter.onCreateViewHolder(parent, 0)

        assertTrue(outgoing is MessageAdapter.OutgoingViewHolder)
        assertTrue(incoming is MessageAdapter.IncomingViewHolder)
    }

    @Test
    fun `adapter binds an outgoing message from the list and recycles it`() {
        val adapter = adapter()
        adapter.submitList(listOf(message(uuid = "mine", senderUuid = "me")))

        val holder = adapter.onCreateViewHolder(parent, 1)
        adapter.onBindViewHolder(holder, 0)

        verify { fx.ivStatus.visibility = android.view.View.GONE }
        verify { fx.tvTime.setText(any<CharSequence>()) }

        adapter.onViewRecycled(holder)
        adapter.release()
    }

    @Test
    fun `adapter binds an incoming message from the list`() {
        val adapter = adapter()
        adapter.submitList(listOf(message(uuid = "in", senderUuid = "other", senderName = "Bob")))

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        verify { fx.tvSenderName.setText("Bob") }
    }

    @Test
    fun `adapter resolves the reply quote from the already loaded list`() {
        val adapter = adapter()
        adapter.submitList(
            listOf(
                message(uuid = "orig", senderUuid = "other", senderName = "Bob", text = "original"),
                message(
                    uuid = "reply",
                    senderUuid = "me",
                    replyToMessageUuid = "orig",
                    text = "answer"
                )
            )
        )

        val holder = adapter.onCreateViewHolder(parent, 1)
        adapter.onBindViewHolder(holder, 1)

        verify { fx.replyQuote.visibility = android.view.View.VISIBLE }
        verify { fx.tvReplyText.setText("original") }
    }

    @Test
    fun `audio state only notifies the affected rows`() {
        val adapter = adapter()
        adapter.submitList(
            listOf(
                message(uuid = "a", senderUuid = "me"),
                message(uuid = "b", senderUuid = "me")
            )
        )

        adapter.setAudioState("a", true)
        assertEquals("a", adapter.playingAudioUuid)

        // Повтор с теми же значениями — ранний выход.
        adapter.setAudioState("a", true)
        assertEquals("a", adapter.playingAudioUuid)

        // Переключение на другую строку и сброс.
        adapter.setAudioState("b", false)
        adapter.setAudioState(null, false)
        assertNull(adapter.playingAudioUuid)
    }

    @Test
    fun `set audio state for an unknown uuid is a no-op`() {
        val adapter = adapter()
        adapter.submitList(listOf(message(uuid = "a", senderUuid = "me")))

        adapter.setAudioState("missing", true)

        assertEquals("missing", adapter.playingAudioUuid)
    }

    @Test
    fun `updateList replaces items and runs the commit callback`() {
        val adapter = adapter()
        var committed = false

        adapter.updateList(listOf(message(uuid = "a", senderUuid = "me"))) { committed = true }

        assertTrue(committed)
        assertEquals(1, adapter.itemCount)
        adapter.release()
    }

    @Test
    fun `updateList without a callback still replaces items`() {
        val adapter = adapter()

        adapter.updateList(listOf(message(uuid = "a", senderUuid = "me")))

        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `diff callback compares by uuid and content`() {
        val callback = MessageAdapter.MessageDiffCallback()
        val a = message(uuid = "x")
        val sameUuid = message(uuid = "x", text = "changed")
        val otherUuid = message(uuid = "y")

        assertTrue(callback.areItemsTheSame(a, sameUuid))
        assertTrue(!callback.areItemsTheSame(a, otherUuid))
        assertTrue(callback.areContentsTheSame(a, message(uuid = "x")))
        assertTrue(!callback.areContentsTheSame(a, sameUuid))
    }

}
