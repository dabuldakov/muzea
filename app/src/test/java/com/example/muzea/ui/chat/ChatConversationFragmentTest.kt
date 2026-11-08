package com.example.muzea.ui.chat

import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.HiltTestActivity
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.di.ChatRepositoryModule
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.repository.AvatarRepository
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.domain.repository.ChatSessionRepository
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.repository.GifRepository
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.data.repository.ChatMessagesCache
import com.example.muzea.data.repository.OutboxEvent
import com.example.muzea.data.repository.OutboxSender
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric+Hilt тесты переписки: заголовок, пустое состояние, рендер
 * сообщений и отправка текста через очередь.
 */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class ChatConversationFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val chatRepository: ChatRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val messageRepository: MessageRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val gifRepository: GifRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val contactRepository: ContactRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val avatarRepository: AvatarRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val chatSessionRepository: ChatSessionRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val outboxSender: OutboxSender = mockk(relaxed = true)

    private val outboxEvents = MutableSharedFlow<OutboxEvent>()

    @Before
    fun setUp() {
        hiltRule.inject()
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("chat_user_uuid", "me").commit()
        ChatMessagesCache.clear()
        every { messageRepository.cachedMessages(any()) } returns emptyList()
        coEvery { messageRepository.loadMessages(any()) } returns flowOf(Resource.Success(emptyList()))
        coEvery { messageRepository.loadMessagesAfter(any(), any()) } returns
            flowOf(Resource.Success(emptyList()))
        coEvery { messageRepository.loadChatReceipts(any()) } returns emptyList()
        coEvery { messageRepository.loadPinned(any()) } returns emptyList()
        coEvery { messageRepository.markMessagesAsRead(any(), any()) } returns true
        coEvery { messageRepository.markMessagesAsDelivered(any(), any()) } returns true
        every { outboxSender.events } returns outboxEvents
        every { outboxSender.pendingForChat(any()) } returns emptyList()
    }

    private fun message(uuid: String = "m1", text: String = "привет") = Message(
        messageUuid = uuid,
        messageId = 1,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = "me",
        senderName = "Me",
        senderAvatar = null,
        text = text,
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01T00:00:00",
        updatedAt = null
    )

    private fun awaitItems(list: RecyclerView, count: Int) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (list.adapter?.itemCount == count) return
            Thread.sleep(10)
        }
        assertEquals(count, list.adapter?.itemCount)
    }

    private fun layout(list: RecyclerView) {
        list.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 1080, 2400)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun launchWithActivity(): Pair<HiltTestActivity, ChatConversationFragment> {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = ChatConversationFragment.newInstance("chat-1", "Баги")
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to fragment
    }

    private fun launch(): ChatConversationFragment {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = ChatConversationFragment.newInstance("chat-1", "Баги")
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun `renders title and empty conversation`() {
        val fragment = launch()

        val title = fragment.requireView().findViewById<android.widget.TextView>(R.id.tvTitle)
        assertEquals("Баги", title.text.toString())
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewMessages)
        assertEquals(0, list.adapter?.itemCount)
    }

    @Test
    fun `renders messages from the repository`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()

        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewMessages)
        assertEquals(1, list.adapter?.itemCount)
    }

    @Test
    fun `clicking a message opens the actions sheet`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewMessages)
        awaitItems(list, 1)
        layout(list)

        list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            true,
            fragment.childFragmentManager.findFragmentByTag("message_actions") != null
        )
    }

    @Test
    fun `select action enters selection mode and close exits`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_SELECT)

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.selectionBar).visibility
        )
        assertEquals(
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.composerBar).visibility
        )
        fragment.requireView().findViewById<View>(R.id.btnSelectionClose).performClick()
        assertEquals(
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.selectionBar).visibility
        )
    }

    @Test
    fun `reply action shows the reply banner`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_REPLY)

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.replyBanner).visibility
        )
    }

    @Test
    fun `edit action fills the input`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_EDIT)

        val input = fragment.requireView().findViewById<android.widget.EditText>(R.id.etMessageInput)
        assertEquals("привет", input.text.toString())
    }

    @Test
    fun `copy action puts the text into the clipboard`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_COPY)

        val clipboard = fragment.requireContext()
            .getSystemService(android.content.ClipboardManager::class.java)
        assertEquals("привет", clipboard.primaryClip?.getItemAt(0)?.text.toString())
    }

    @Test
    fun `back button and title use the navigator`() {
        val (activity, fragment) = launchWithActivity()

        fragment.requireView().findViewById<View>(R.id.btnBack).performClick()
        fragment.requireView().findViewById<View>(R.id.tvTitle).performClick()

        assertEquals(true, activity.backCalled)
        assertEquals("chat-1", activity.lastGroupSettings?.chatUuid)
    }

    @Test
    fun `delete selected shows the confirmation dialog`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()
        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_SELECT)

        fragment.requireView().findViewById<View>(R.id.btnSelectionDelete).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            true,
            org.robolectric.shadows.ShadowDialog.getLatestDialog()?.isShowing
        )
    }

    @Test
    fun `forward selected opens the chat picker`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        coEvery { chatRepository.loadChats() } returns
            flowOf(Resource.Success(emptyList()))
        val fragment = launch()
        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_SELECT)

        fragment.requireView().findViewById<View>(R.id.btnSelectionForward).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            true,
            fragment.childFragmentManager.findFragmentByTag("forward_picker") != null
        )
    }

    @Test
    fun `pin and unpin actions call the repository`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        coEvery { messageRepository.pinMessage("chat-1", "m1") } returns true
        coEvery { messageRepository.unpinMessage("chat-1", "m1") } returns true
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_PIN)
        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_UNPIN)
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { messageRepository.pinMessage("chat-1", "m1") }
        io.mockk.coVerify { messageRepository.unpinMessage("chat-1", "m1") }
    }

    @Test
    fun `delete action confirms and deletes the message`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        coEvery { messageRepository.deleteMessage("m1") } returns true
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_DELETE)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            as androidx.appcompat.app.AlertDialog
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { messageRepository.deleteMessage("m1") }
    }

    @Test
    fun `block action opens the confirmation dialog`() {
        every { messageRepository.cachedMessages("chat-1") } returns
            listOf(message().copy(senderUuid = "other"))
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_BLOCK)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            true,
            org.robolectric.shadows.ShadowDialog.getLatestDialog()?.isShowing
        )
    }

    @Test
    fun `forward action opens the chat picker`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        coEvery { chatRepository.loadChats() } returns flowOf(Resource.Success(emptyList()))
        val fragment = launch()

        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_FORWARD)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            true,
            fragment.childFragmentManager.findFragmentByTag("forward_picker") != null
        )
    }

    @Test
    fun `reply can be cancelled from the banner`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        val fragment = launch()
        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_REPLY)

        fragment.requireView().findViewById<View>(R.id.btnCancelReply).performClick()

        assertEquals(
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.replyBanner).visibility
        )
    }

    @Test
    fun `send after edit submits the edited text`() {
        every { messageRepository.cachedMessages("chat-1") } returns listOf(message())
        coEvery { messageRepository.editMessage("m1", "исправлено") } returns
            flowOf(Resource.Success(message()))
        val fragment = launch()
        fragment.onMessageAction("m1", MessageActionsBottomSheet.ACTION_EDIT)
        shadowOf(Looper.getMainLooper()).idle()
        fragment.requireView().findViewById<android.widget.EditText>(R.id.etMessageInput)
            .setText("исправлено")

        fragment.requireView().findViewById<View>(R.id.btnSend).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { messageRepository.editMessage("m1", "исправлено") }
    }

    @Test
    fun `empty composer cycles send mode without crashing`() {
        val fragment = launch()

        repeat(3) { fragment.requireView().findViewById<View>(R.id.btnSend).performClick() }

        assertTrue(true)
    }

    @Test
    fun `pinned message is shown in the pinned bar`() {
        coEvery { messageRepository.loadPinned("chat-1") } returns listOf(message(text = "закреп"))

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.pinnedBar).visibility
        )
        val preview = fragment.requireView()
            .findViewById<android.widget.TextView>(R.id.tvPinnedText)
        assertEquals("закреп", preview.text.toString())
    }

    @Test
    fun `attach button opens the attachments sheet`() {
        val fragment = launch()

        fragment.requireView().findViewById<View>(R.id.btnAdd).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val sheet = fragment.childFragmentManager.findFragmentByTag("attachments")
        assertEquals(true, sheet is AttachmentsBottomSheet)
    }

    @Test
    fun `send button queues the typed message and clears the input`() {
        val fragment = launch()

        val input = fragment.requireView().findViewById<android.widget.EditText>(R.id.etMessageInput)
        input.setText("привет")
        fragment.requireView().findViewById<View>(R.id.btnSend).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("", input.text.toString())
        verify { outboxSender.enqueue(any()) }
    }
}
